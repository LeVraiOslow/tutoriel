package fr.ivalona.tutoriel.cinematic;

import fr.ivalona.tutoriel.TutorielPlugin;
import fr.ivalona.tutoriel.data.PlayerData;
import fr.ivalona.tutoriel.util.Fireworks;
import fr.ivalona.tutoriel.util.Fx;
import fr.ivalona.tutoriel.util.Locs;
import fr.ivalona.tutoriel.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Cinematique d'arrivee : camera (entite spectee) qui suit une trajectoire, avec une timeline
 * d'evenements configurables (titres, sons, feux d'artifice, particules, effets...).
 */
public final class CinematicManager {
    private record Ev(int tick, String type, ConfigurationSection cfg) {}

    private static final class ActiveFx {
        final ConfigurationSection cfg;
        final int startTick;
        final int endTick;

        ActiveFx(ConfigurationSection cfg, int startTick, int endTick) {
            this.cfg = cfg;
            this.startTick = startTick;
            this.endTick = endTick;
        }
    }

    private static final class Session {
        final Player player;
        final CameraPath path;
        final Location center;
        final boolean preview;
        Entity camera;
        GameMode prevMode;
        Location prevLoc;
        int tick;
        int nextEv;
        boolean ending;
        BukkitTask task;
        final List<ActiveFx> fx = new ArrayList<>();
        final Set<PotionEffectType> effects = new HashSet<>();

        Session(Player player, CameraPath path, Location center, boolean preview) {
            this.player = player;
            this.path = path;
            this.center = center;
            this.preview = preview;
        }
    }

    private final TutorielPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, BukkitTask> waiting = new HashMap<>();
    private final Set<UUID> packDone = new HashSet<>();
    private final List<Ev> timeline = new ArrayList<>();
    private final File storeFile;
    private YamlConfiguration store;
    private final Random rnd = new Random();

    public CinematicManager(TutorielPlugin plugin) {
        this.plugin = plugin;
        this.storeFile = new File(plugin.getDataFolder(), "cinematic-path.yml");
    }

    // ------------------------------------------------------------------ configuration

    public void reload() {
        timeline.clear();
        for (Map<?, ?> m : plugin.getConfig().getMapList("cinematic.timeline")) {
            ConfigurationSection s = new MemoryConfiguration().createSection("e", m);
            timeline.add(new Ev((int) Math.round(s.getDouble("at", 0) * 20), s.getString("type", "").toUpperCase(Locale.ROOT), s));
        }
        timeline.sort(Comparator.comparingInt(Ev::tick));
        store = YamlConfiguration.loadConfiguration(storeFile);
    }

    private void saveStore() {
        try {
            plugin.getDataFolder().mkdirs();
            store.save(storeFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'ecrire cinematic-path.yml : " + e.getMessage());
        }
    }

    public Location center() {
        Location c = Locs.parse(store.getString("center"));
        if (c != null) return c;
        World w = Bukkit.getWorld(plugin.getConfig().getString("cinematic.world", ""));
        if (w == null) w = Bukkit.getWorlds().get(0);
        return w.getSpawnLocation().clone();
    }

    public void setCenter(Location l) {
        store.set("center", Locs.format(l));
        saveStore();
    }

    public int addPoint(Location l, double seconds) {
        List<Map<?, ?>> list = new ArrayList<>(store.getMapList("waypoints"));
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("world", l.getWorld().getName());
        m.put("x", l.getX());
        m.put("y", l.getY());
        m.put("z", l.getZ());
        m.put("yaw", (double) l.getYaw());
        m.put("pitch", (double) l.getPitch());
        m.put("seconds", seconds);
        list.add(m);
        store.set("waypoints", list);
        saveStore();
        return list.size();
    }

    public void clearPoints() {
        store.set("waypoints", null);
        saveStore();
    }

    public int pointCount() {
        return store.getMapList("waypoints").size();
    }

    private CameraPath buildPath() {
        List<Map<?, ?>> raw = store.getMapList("waypoints");
        if (raw.size() >= 2) {
            World w = Bukkit.getWorld(String.valueOf(raw.get(0).get("world")));
            if (w != null) {
                List<SplinePath.Point> pts = new ArrayList<>();
                for (Map<?, ?> m : raw) {
                    pts.add(new SplinePath.Point(num(m, "x"), num(m, "y"), num(m, "z"), num(m, "yaw"), num(m, "pitch"),
                            (int) Math.round(num(m, "seconds") * 20)));
                }
                return new SplinePath(w, pts);
            }
        }
        Location c = center();
        return new AutoOrbitPath(c.getWorld(), c.getX(), c.getY(), c.getZ(),
                plugin.getConfig().getConfigurationSection("cinematic.auto-path"));
    }

    private static double num(Map<?, ?> m, String k) {
        Object o = m.get(k);
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    // ------------------------------------------------------------------ etat

    public boolean isIn(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    /** Restaure l'etat d'un joueur dont la cinematique a ete interrompue par un crash. */
    public void restoreIfNeeded(Player p, PlayerData d) {
        if (d.restoreGameMode == null && d.restoreLocation == null) return;
        try {
            if (d.restoreGameMode != null) p.setGameMode(GameMode.valueOf(d.restoreGameMode));
        } catch (IllegalArgumentException ignored) {
        }
        Location l = Locs.parse(d.restoreLocation);
        if (l != null) p.teleport(l);
        d.restoreGameMode = null;
        d.restoreLocation = null;
        plugin.data().save(p.getUniqueId(), d);
    }

    // ------------------------------------------------------------------ demarrage

    /** Attend un delai minimum (et le resource pack Nexo) avant de lancer la cinematique. */
    public void queueStart(Player p) {
        UUID id = p.getUniqueId();
        cancelWaiting(id);
        int min = plugin.getConfig().getInt("tutorial.start-delay-ticks", 60);
        boolean wait = plugin.getConfig().getBoolean("tutorial.wait-for-resource-pack", true);
        int timeout = plugin.getConfig().getInt("tutorial.resource-pack-timeout-ticks", 300);
        int[] t = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) {
                cancelWaiting(id);
                return;
            }
            t[0] += 5;
            boolean ready = t[0] >= min && (!wait || packDone.contains(id) || t[0] >= timeout);
            if (ready) {
                cancelWaiting(id);
                packDone.remove(id);
                start(p, false);
            }
        }, 5L, 5L);
        waiting.put(id, holder[0]);
    }

    private void cancelWaiting(UUID id) {
        BukkitTask t = waiting.remove(id);
        if (t != null) t.cancel();
    }

    public void packStatus(Player p, PlayerResourcePackStatusEvent.Status st) {
        String n = st.name();
        if (!n.equals("ACCEPTED") && !n.equals("DOWNLOADED")) packDone.add(p.getUniqueId());
    }

    public void start(Player p, boolean preview) {
        UUID id = p.getUniqueId();
        if (sessions.containsKey(id)) return;
        cancelWaiting(id);
        CameraPath path = buildPath();
        Session s = new Session(p, path, center(), preview);
        s.prevMode = p.getGameMode();
        s.prevLoc = p.getLocation().clone();
        sessions.put(id, s);
        if (!preview) {
            PlayerData d = plugin.data().get(id);
            if (d != null) {
                d.restoreGameMode = s.prevMode.name();
                d.restoreLocation = Locs.format(s.prevLoc);
                plugin.data().save(id, d);
            }
        }
        CameraPath.Frame f = path.at(0);
        Location start = new Location(path.world(), f.x(), f.y(), f.z(), f.yaw(), f.pitch());
        p.teleportAsync(start).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ok) begin(s, start);
            else abort(p);
        }));
    }

    private void begin(Session s, Location start) {
        Player p = s.player;
        if (!p.isOnline() || sessions.get(p.getUniqueId()) != s) return;
        s.camera = spawnCamera(start);
        p.setGameMode(GameMode.SPECTATOR);
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(s), 1L, 1L);
    }

    private Entity spawnCamera(Location loc) {
        World w = loc.getWorld();
        if ("ARMOR_STAND".equalsIgnoreCase(plugin.getConfig().getString("cinematic.camera-entity", "ITEM_DISPLAY"))) {
            return w.spawn(loc, ArmorStand.class, a -> {
                a.setVisible(false);
                a.setMarker(true);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setSilent(true);
                a.setPersistent(false);
            });
        }
        int dur = plugin.getConfig().getInt("cinematic.teleport-duration", 2);
        return w.spawn(loc, ItemDisplay.class, d -> {
            d.setTeleportDuration(dur);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setSilent(true);
        });
    }

    // ------------------------------------------------------------------ boucle

    private void tick(Session s) {
        Player p = s.player;
        if (!p.isOnline() || s.camera == null || !s.camera.isValid()) {
            abort(p);
            return;
        }
        CameraPath.Frame f = s.path.at(s.tick);
        s.camera.teleport(new Location(s.path.world(), f.x(), f.y(), f.z(), f.yaw(), f.pitch()));
        if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.SPECTATOR);
        if (!s.camera.equals(p.getSpectatorTarget())) p.setSpectatorTarget(s.camera);

        if (plugin.getConfig().getBoolean("cinematic.allow-skip", true) && s.tick % 40 == 0 && s.tick <= 160) {
            p.sendActionBar(Text.parse(plugin.messages().str("cinematic-skip-hint"), p, null));
        }
        while (s.nextEv < timeline.size() && timeline.get(s.nextEv).tick() <= s.tick) {
            run(s, timeline.get(s.nextEv++));
        }
        for (Iterator<ActiveFx> it = s.fx.iterator(); it.hasNext(); ) {
            ActiveFx a = it.next();
            if (s.tick >= a.endTick) {
                it.remove();
                continue;
            }
            particles(s, a.cfg, s.tick - a.startTick);
        }
        s.tick++;
        if (s.tick >= s.path.totalTicks()) finish(s, false);
    }

    private void run(Session s, Ev ev) {
        Player p = s.player;
        ConfigurationSection c = ev.cfg();
        Map<String, String> vars = Map.of("player", p.getName());
        switch (ev.type()) {
            case "TITLE" -> Fx.title(p, c, vars);
            case "ACTIONBAR" -> p.sendActionBar(Text.parse(c.getString("text", ""), p, vars));
            case "MESSAGE" -> p.sendMessage(Text.parse(c.getString("text", ""), p, vars));
            case "SOUND" -> Fx.sound(p, c.getString("sound", ""), s.camera.getLocation());
            case "STOP_SOUND" -> p.stopAllSounds();
            case "TIME" -> p.setPlayerTime(c.getLong("time", 0), false);
            case "EFFECT" -> {
                NamespacedKey key = NamespacedKey.fromString(c.getString("effect", "blindness").toLowerCase(Locale.ROOT));
                PotionEffectType t = key == null ? null : PotionEffectType.getByKey(key);
                if (t != null) {
                    p.addPotionEffect(new PotionEffect(t, c.getInt("duration", 40), 0, false, false, false));
                    s.effects.add(t);
                }
            }
            case "FIREWORK" -> Fireworks.burst(plugin, s.center, c.getInt("count", 5), c.getDouble("radius", 20),
                    c.getDouble("height", 15), c.getStringList("colors"));
            case "PARTICLES" -> {
                int dur = Math.max(1, (int) Math.round(c.getDouble("duration", 0) * 20));
                s.fx.add(new ActiveFx(c, s.tick, s.tick + dur));
            }
            case "COMMAND" -> {
                String cmd = c.getString("command", "").replace("{player}", p.getName());
                if (!cmd.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
            default -> plugin.getLogger().warning("Evenement de cinematique inconnu : " + ev.type());
        }
    }

    private void particles(Session s, ConfigurationSection c, int local) {
        NamespacedKey key = NamespacedKey.fromString(c.getString("particle", "end_rod").toLowerCase(Locale.ROOT));
        Particle part = key == null ? null : Registry.PARTICLE_TYPE.get(key);
        if (part == null) return;
        Player p = s.player;
        Location o = s.center;
        double oy = c.getDouble("offset-y", 0);
        double radius = c.getDouble("radius", 5);
        double height = c.getDouble("height", 10);
        int pts = Math.max(1, c.getInt("points", 12));
        double speed = c.getDouble("speed", 0);
        switch (c.getString("shape", "BURST").toUpperCase(Locale.ROOT)) {
            case "RING" -> {
                for (int i = 0; i < pts; i++) {
                    double a = Math.PI * 2 * i / pts + local * 0.05;
                    p.spawnParticle(part, o.getX() + Math.cos(a) * radius, o.getY() + oy, o.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, speed);
                }
            }
            case "SPIRAL" -> {
                for (int i = 0; i < pts; i++) {
                    double frac = i / (double) pts;
                    double a = frac * Math.PI * 4 + local * 0.12;
                    p.spawnParticle(part, o.getX() + Math.cos(a) * radius, o.getY() + oy + frac * height, o.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, speed);
                }
            }
            case "COLUMN" -> {
                for (int i = 0; i < pts; i++) {
                    p.spawnParticle(part, o.getX() + (rnd.nextDouble() - 0.5) * radius, o.getY() + oy + rnd.nextDouble() * height,
                            o.getZ() + (rnd.nextDouble() - 0.5) * radius, 1, 0, 0, 0, speed);
                }
            }
            default -> {
                for (int i = 0; i < pts; i++) {
                    double dx = rnd.nextGaussian(), dy = rnd.nextGaussian(), dz = rnd.nextGaussian();
                    double len = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
                    double r = radius * rnd.nextDouble();
                    p.spawnParticle(part, o.getX() + dx / len * r, o.getY() + oy + dy / len * r, o.getZ() + dz / len * r, 1, 0, 0, 0, speed);
                }
            }
        }
    }

    // ------------------------------------------------------------------ fin

    public boolean skip(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return false;
        if (!s.preview && !plugin.getConfig().getBoolean("cinematic.allow-skip", true)) return false;
        finish(s, true);
        return true;
    }

    private void cleanupSession(Session s) {
        if (s.task != null) s.task.cancel();
        if (s.camera != null && s.camera.isValid()) s.camera.remove();
        Player p = s.player;
        if (p.isOnline()) {
            p.setGameMode(s.prevMode);
            for (PotionEffectType t : s.effects) p.removePotionEffect(t);
            p.resetPlayerTime();
            p.stopAllSounds();
        }
    }

    private void finish(Session s, boolean skipped) {
        if (s.ending) return;
        s.ending = true;
        Player p = s.player;
        sessions.remove(p.getUniqueId());
        cleanupSession(s);
        Location dest = Locs.parse(plugin.getConfig().getString("cinematic.end-location", ""));
        if (dest == null) dest = s.prevLoc;
        p.teleportAsync(dest).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> afterEnd(s)));
    }

    private void afterEnd(Session s) {
        Player p = s.player;
        if (!p.isOnline()) return;
        for (String cmd : plugin.getConfig().getStringList("cinematic.end-commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }
        if (s.preview) return;
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) d = plugin.data().create(p.getUniqueId());
        d.cinematicDone = true;
        d.restoreGameMode = null;
        d.restoreLocation = null;
        plugin.data().save(p.getUniqueId(), d);
        plugin.messages().bundle(p, "cinematic-end", Map.of("player", p.getName()));
        plugin.tutorial().start(p);
    }

    /** Interruption (deconnexion, arret du serveur) : on restaure le joueur sans valider la cinematique. */
    public void abort(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        s.ending = true;
        cleanupSession(s);
        if (p.isOnline() && s.prevLoc != null) p.teleport(s.prevLoc);
        if (!s.preview) {
            PlayerData d = plugin.data().get(p.getUniqueId());
            if (d != null) {
                d.restoreGameMode = null;
                d.restoreLocation = null;
                plugin.data().save(p.getUniqueId(), d);
            }
        }
    }

    public void onQuit(Player p) {
        cancelWaiting(p.getUniqueId());
        packDone.remove(p.getUniqueId());
        abort(p);
    }

    public void shutdown() {
        for (Session s : new ArrayList<>(sessions.values())) abort(s.player);
        for (UUID id : new ArrayList<>(waiting.keySet())) cancelWaiting(id);
    }
}
