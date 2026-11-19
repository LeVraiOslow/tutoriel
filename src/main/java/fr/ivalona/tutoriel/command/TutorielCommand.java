package fr.ivalona.tutoriel.command;

import fr.ivalona.tutoriel.TutorielPlugin;
import fr.ivalona.tutoriel.data.PlayerData;
import fr.ivalona.tutoriel.tutorial.Quest;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TutorielCommand implements CommandExecutor, TabCompleter {
    private final TutorielPlugin plugin;

    public TutorielCommand(TutorielPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("info")) {
            info(sender);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("skip")) {
            if (!(sender instanceof Player p)) return true;
            if (!plugin.cinematic().skip(p)) plugin.messages().send(sender, "commands.skip-none", null);
            return true;
        }
        if (!sender.hasPermission("tutoriel.admin")) {
            plugin.messages().send(sender, "commands.no-permission", null);
            return true;
        }
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.messages().send(sender, "commands.reloaded", null);
            }
            case "cinematic" -> cinematic(sender, a);
            case "start", "reset", "complete", "vote", "setstep" -> targeted(sender, sub, a);
            default -> plugin.messages().send(sender, "commands.usage", null);
        }
        return true;
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "commands.usage", null);
            return;
        }
        PlayerData d = plugin.data().get(p.getUniqueId());
        Quest q = plugin.tutorial().current(d);
        if (q != null) {
            plugin.messages().bundle(p, "quest-resume", plugin.tutorial().vars(p, d, q));
        } else if (d != null && d.step > plugin.tutorial().total()) {
            plugin.messages().send(sender, "commands.finished", null);
        } else {
            plugin.messages().send(sender, "commands.not-active", null);
        }
    }

    private void cinematic(CommandSender sender, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "preview" -> {
                Player target = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    plugin.messages().send(sender, "commands.player-not-found", null);
                    return;
                }
                plugin.cinematic().start(target, true);
            }
            case "setcenter" -> {
                if (!(sender instanceof Player p)) return;
                plugin.cinematic().setCenter(p.getLocation());
                plugin.messages().send(sender, "commands.center-set", null);
            }
            case "addpoint" -> {
                if (!(sender instanceof Player p)) return;
                double sec = 5;
                if (a.length > 2) {
                    try {
                        sec = Double.parseDouble(a[2].replace(',', '.'));
                    } catch (NumberFormatException ignored) {
                    }
                }
                int n = plugin.cinematic().addPoint(p.getLocation(), sec);
                plugin.messages().send(sender, "commands.point-added", Map.of("count", String.valueOf(n), "seconds", String.valueOf(sec)));
            }
            case "clearpoints" -> {
                plugin.cinematic().clearPoints();
                plugin.messages().send(sender, "commands.points-cleared", null);
            }
            default -> plugin.messages().send(sender, "commands.usage", null);
        }
    }

    private void targeted(CommandSender sender, String sub, String[] a) {
        if (a.length < 2) {
            plugin.messages().send(sender, "commands.usage", null);
            return;
        }
        if (sub.equals("vote")) {
            plugin.tutorial().handleVote(a[1]);
            plugin.messages().send(sender, "commands.done", null);
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) {
            plugin.messages().send(sender, "commands.player-not-found", null);
            return;
        }
        switch (sub) {
            case "start" -> {
                PlayerData d = plugin.data().create(t.getUniqueId());
                d.cinematicDone = false;
                plugin.data().save(t.getUniqueId(), d);
                plugin.cinematic().start(t, false);
            }
            case "reset" -> plugin.tutorial().start(t);
            case "complete" -> plugin.tutorial().forceComplete(t);
            case "setstep" -> {
                int n = 1;
                if (a.length > 2) {
                    try {
                        n = Integer.parseInt(a[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                plugin.tutorial().setStep(t, n);
            }
            default -> {
            }
        }
        plugin.messages().send(sender, "commands.done", null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.add("info");
            out.add("skip");
            if (sender.hasPermission("tutoriel.admin")) {
                out.addAll(List.of("reload", "start", "reset", "setstep", "complete", "vote", "cinematic"));
            }
        } else if (sender.hasPermission("tutoriel.admin")) {
            if (a.length == 2 && a[0].equalsIgnoreCase("cinematic")) {
                out.addAll(List.of("preview", "setcenter", "addpoint", "clearpoints"));
            } else if (a.length == 2 && List.of("start", "reset", "setstep", "complete", "vote").contains(a[0].toLowerCase(Locale.ROOT))
                    || a.length == 3 && a[0].equalsIgnoreCase("cinematic") && a[1].equalsIgnoreCase("preview")) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
