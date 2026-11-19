package fr.ivalona.tutoriel.listener;

import fr.ivalona.tutoriel.TutorielPlugin;
import fr.ivalona.tutoriel.data.DataManager;
import fr.ivalona.tutoriel.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Locale;

public final class TutorialListener implements Listener {
    private final TutorielPlugin plugin;

    public TutorialListener(TutorielPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("tutoriel.bypass")) return;
        DataManager dm = plugin.data();
        PlayerData d = dm.get(p.getUniqueId());
        if (d == null) {
            if (p.hasPlayedBefore() && plugin.getConfig().getBoolean("tutorial.only-new-players", true)) return;
            d = dm.create(p.getUniqueId());
        }
        plugin.cinematic().restoreIfNeeded(p, d);
        if (!d.cinematicDone) {
            if (plugin.getConfig().getBoolean("cinematic.enabled", true)) {
                plugin.cinematic().queueStart(p);
            } else {
                d.cinematicDone = true;
                dm.save(p.getUniqueId(), d);
                plugin.tutorial().start(p);
            }
        } else if (plugin.tutorial().isActive(d)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.tutorial().resume(p), 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.cinematic().onQuit(e.getPlayer());
        plugin.tutorial().onQuit(e.getPlayer());
    }

    /** Pendant la cinematique, seules les commandes /tutoriel skip sont autorisees. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommandLock(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.cinematic().isIn(p)) return;
        String[] t = e.getMessage().trim().toLowerCase(Locale.ROOT).split("\\s+");
        String label = t[0].startsWith("/") ? t[0].substring(1) : t[0];
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        boolean skip = (label.equals("tutoriel") || label.equals("tuto")) && t.length > 1 && t[1].equals("skip");
        if (!skip) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (plugin.cinematic().isIn(p)) return;
        plugin.tutorial().handleCommand(p, e.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c != PlayerTeleportEvent.TeleportCause.COMMAND && c != PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        plugin.tutorial().handleTeleport(e.getPlayer(), e.getFrom(), e.getTo());
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent e) {
        plugin.cinematic().packStatus(e.getPlayer(), e.getStatus());
    }
}
