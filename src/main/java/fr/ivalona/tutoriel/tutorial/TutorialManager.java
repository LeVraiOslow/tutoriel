package fr.ivalona.tutoriel.tutorial;

import fr.ivalona.tutoriel.TutorielPlugin;
import fr.ivalona.tutoriel.data.PlayerData;
import fr.ivalona.tutoriel.util.Fireworks;
import fr.ivalona.tutoriel.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Moteur du tutoriel : quetes dans l'ordre, recompenses Vault, bossbar native optionnelle. */
public final class TutorialManager {
    private record PendingWarp(String questId, String key, long expires) {}

    private final TutorielPlugin plugin;
    private List<Quest> quests = List.of();
    private final Map<UUID, PendingWarp> pendingWarp = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public TutorialManager(TutorielPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ chargement

    public void load() {
        List<Quest> list = new ArrayList<>();
        int n = 0;
        for (Map<?, ?> raw : plugin.getConfig().getMapList("quests")) {
            n++;
            ConfigurationSection s = new MemoryConfiguration().createSection("q", raw);
            String id = s.getString("id", "quete" + n);
            List<Condition> conds = new ArrayList<>();
            for (Map<?, ?> cm : s.getMapList("conditions")) {
                ConfigurationSection c = new MemoryConfiguration().createSection("c", cm);
                Condition.Type type;
                try {
                    type = Condition.Type.valueOf(c.getString("type", "COMMAND").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Quete " + id + " : type de condition inconnu, ignore.");
                    continue;
                }
                List<String[]> cmds = new ArrayList<>();
                for (String cs : c.getStringList("commands")) {
                    String t = cs.trim().toLowerCase(Locale.ROOT);
                    if (t.startsWith("/")) t = t.substring(1);
                    if (!t.isEmpty()) cmds.add(t.split("\\s+"));
                }
                conds.add(new Condition(type, cmds, c.getInt("min-args", 0), c.getInt("delay-ticks", 1)));
            }
            if (conds.isEmpty()) {
                plugin.getLogger().warning("Quete " + id + " ignoree : aucune condition valide.");
                continue;
            }
            String title = s.getString("title", id);
            list.add(new Quest(id, title, s.getString("description", ""), s.getString("bossbar", title),
                    s.getDouble("reward", 0), s.getStringList("reward-commands"), conds));
        }
        quests = List.copyOf(list);
        plugin.getLogger().info(quests.size() + " quete(s) de tutoriel chargee(s).");
    }

    public int total() {
        return quests.size();
    }

    public Quest current(PlayerData d) {
        if (d == null || d.step < 1 || d.step > quests.size()) return null;
        return quests.get(d.step - 1);
    }

    public boolean isActive(PlayerData d) {
        return current(d) != null;
    }

    // ------------------------------------------------------------------ textes

    public String formatAmount(double amount) {
        DecimalFormatSymbols sy = new DecimalFormatSymbols(Locale.FRANCE);
        sy.setGroupingSeparator('.');
        sy.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", sy).format(amount);
    }

    public String rewardText(Quest q) {
        return plugin.messages().str("reward-format").replace("{amount}", formatAmount(q.reward()));
    }

    public Map<String, String> vars(Player p, PlayerData d, Quest q) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("player", p == null ? "" : p.getName());
        v.put("total", String.valueOf(total()));
        if (d != null) v.put("number", String.valueOf(Math.min(Math.max(d.step, 0), total())));
        if (q != null) {
            v.put("id", q.id());
            v.put("title", q.title());
            v.put("description", q.description());
            v.put("bossbar", q.bossbar());
            v.put("amount", formatAmount(q.reward()));
            v.put("reward", rewardText(q));
        }
        return v;
    }

    public Component barComponent(Player viewer, PlayerData d, Quest q) {
        return Text.parse(plugin.getConfig().getString("bossbar.format", "{bossbar}"), viewer, vars(viewer, d, q));
    }

    // ------------------------------------------------------------------ cycle de vie

    public void start(Player p) {
        if (quests.isEmpty()) return;
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) d = plugin.data().create(p.getUniqueId());
        d.step = 1;
        d.flags.clear();
        plugin.data().save(p.getUniqueId(), d);
        plugin.messages().bundle(p, "quest-start", vars(p, d, current(d)));
        updateBar(p);
    }

    public void resume(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        Quest q = current(d);
        if (q == null) return;
        plugin.messages().bundle(p, "quest-resume", vars(p, d, q));
        updateBar(p);
    }

    public void setStep(Player p, int step) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) d = plugin.data().create(p.getUniqueId());
        d.step = Math.max(0, Math.min(step, total() + 1));
        d.flags.clear();
        d.cinematicDone = true;
        plugin.data().save(p.getUniqueId(), d);
        if (isActive(d)) plugin.messages().bundle(p, "quest-start", vars(p, d, current(d)));
        updateBar(p);
    }

    public void forceComplete(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        Quest q = current(d);
        if (q != null) complete(p, d, q);
    }

    public void onQuit(Player p) {
        pendingWarp.remove(p.getUniqueId());
        hideBar(p);
    }

    // ------------------------------------------------------------------ detection

    public void handleCommand(Player p, String line) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        Quest q = current(d);
        if (q == null) return;
        String[] t = tokens(line);
        if (t.length == 0) return;
        for (int i = 0; i < q.conditions().size(); i++) {
            Condition c = q.conditions().get(i);
            if (c.type() == Condition.Type.VOTE || !matches(c, t) || !commandUsable(p, t[0])) continue;
            String key = q.id() + "#" + i;
            if (c.type() == Condition.Type.WARP) {
                long window = plugin.getConfig().getLong("tutorial.warp-window-ticks", 400) * 50L;
                pendingWarp.put(p.getUniqueId(), new PendingWarp(q.id(), key, System.currentTimeMillis() + window));
            } else {
                long delay = Math.max(1, c.delayTicks());
                Bukkit.getScheduler().runTaskLater(plugin, () -> satisfy(p, q.id(), key), delay);
            }
        }
    }

    public void handleTeleport(Player p, Location from, Location to) {
        PendingWarp w = pendingWarp.get(p.getUniqueId());
        if (w == null) return;
        if (System.currentTimeMillis() > w.expires()) {
            pendingWarp.remove(p.getUniqueId());
            return;
        }
        if (to == null || (from.getWorld() == to.getWorld() && from.distanceSquared(to) < 9)) return;
        pendingWarp.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> satisfy(p, w.questId(), w.key()), 10L);
    }

    public void handleVote(String name) {
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        if (op == null) return;
        PlayerData d = plugin.data().get(op.getUniqueId());
        if (d == null) return;
        d.flags.add("voted");
        plugin.data().save(op.getUniqueId(), d);
        Player p = op.getPlayer();
        Quest q = current(d);
        if (p != null && q != null && hasVote(q) && allDone(d, q)) complete(p, d, q);
    }

    private boolean hasVote(Quest q) {
        return q.conditions().stream().anyMatch(c -> c.type() == Condition.Type.VOTE);
    }

    private String[] tokens(String line) {
        String s = line.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("/")) s = s.substring(1);
        if (s.isEmpty()) return new String[0];
        String[] t = s.split("\\s+");
        int colon = t[0].indexOf(':');
        if (colon >= 0) t[0] = t[0].substring(colon + 1);
        return t;
    }

    private boolean matches(Condition c, String[] t) {
        for (String[] cmd : c.commands()) {
            if (t.length < cmd.length + c.minArgs()) continue;
            boolean ok = true;
            for (int k = 0; k < cmd.length; k++) {
                if (!t[k].equals(cmd[k])) {
                    ok = false;
                    break;
                }
            }
            if (ok) return true;
        }
        return false;
    }

    private boolean commandUsable(Player p, String label) {
        if (!plugin.getConfig().getBoolean("tutorial.verify-command-exists", false)) return true;
        Command cmd = Bukkit.getCommandMap().getCommand(label);
        return cmd != null && cmd.testPermissionSilent(p);
    }

    private boolean allDone(PlayerData d, Quest q) {
        for (int i = 0; i < q.conditions().size(); i++) {
            Condition c = q.conditions().get(i);
            boolean ok = d.flags.contains(q.id() + "#" + i)
                    || (c.type() == Condition.Type.VOTE && d.flags.contains("voted"));
            if (!ok) return false;
        }
        return true;
    }

    private void satisfy(Player p, String questId, String key) {
        if (!p.isOnline()) return;
        PlayerData d = plugin.data().get(p.getUniqueId());
        Quest q = current(d);
        if (q == null || !q.id().equals(questId)) return;
        d.flags.add(key);
        plugin.data().save(p.getUniqueId(), d);
        if (allDone(d, q)) complete(p, d, q);
    }

    // ------------------------------------------------------------------ progression

    private void complete(Player p, PlayerData d, Quest q) {
        if (current(d) != q) return;
        Economy eco = plugin.economy();
        if (eco != null && q.reward() > 0) {
            eco.depositPlayer(p, q.reward());
        } else if (eco == null && q.reward() > 0) {
            plugin.getLogger().warning("Aucune economie Vault trouvee : recompense non versee pour " + p.getName());
        }
        Map<String, String> v = vars(p, d, q);
        for (String c : q.rewardCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), apply(c, v));
        }
        d.flags.removeIf(f -> f.startsWith(q.id() + "#"));
        d.step++;
        plugin.data().save(p.getUniqueId(), d);
        plugin.messages().bundle(p, "quest-complete", v);
        if (d.step > total()) {
            finish(p, d);
        } else {
            Quest next = current(d);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && plugin.data().get(p.getUniqueId()) == d) {
                    plugin.messages().bundle(p, "quest-start", vars(p, d, next));
                }
            }, plugin.getConfig().getLong("tutorial.next-quest-delay-ticks", 60));
            updateBar(p);
        }
    }

    private void finish(Player p, PlayerData d) {
        Map<String, String> v = vars(p, d, null);
        plugin.messages().bundle(p, "tutorial-complete", v);
        for (String c : plugin.getConfig().getStringList("completion.commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), apply(c, v));
        }
        int fw = plugin.getConfig().getInt("completion.fireworks", 0);
        if (fw > 0) Fireworks.burst(plugin, p.getLocation(), fw, 6, 4, List.of());
        hideBar(p);
    }

    private String apply(String s, Map<String, String> vars) {
        for (Map.Entry<String, String> e : vars.entrySet()) {
            s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return s;
    }

    // ------------------------------------------------------------------ bossbar native (optionnelle)

    public void updateBar(Player p) {
        if (!plugin.getConfig().getBoolean("bossbar.native-enabled", false)) return;
        PlayerData d = plugin.data().get(p.getUniqueId());
        Quest q = current(d);
        if (q == null) {
            hideBar(p);
            return;
        }
        Component name = barComponent(p, d, q);
        float prog = Math.max(0f, Math.min(1f, (d.step - 1f) / Math.max(1, total())));
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            BossBar.Color color = BossBar.Color.YELLOW;
            BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;
            try {
                color = BossBar.Color.valueOf(plugin.getConfig().getString("bossbar.color", "YELLOW").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
            try {
                overlay = BossBar.Overlay.valueOf(plugin.getConfig().getString("bossbar.overlay", "PROGRESS").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
            bar = BossBar.bossBar(name, prog, color, overlay);
            bars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(name);
            bar.progress(prog);
        }
    }

    public void hideBar(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
    }

    public void hideAllBars() {
        for (Player p : Bukkit.getOnlinePlayers()) hideBar(p);
    }
}
