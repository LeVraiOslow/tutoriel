package fr.ivalona.tutoriel.hook;

import fr.ivalona.tutoriel.TutorielPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Ecoute VotifierEvent (NuVotifier) par reflexion, sans dependance a la compilation. */
public final class VoteHook {
    private VoteHook() {}

    public static void register(TutorielPlugin plugin) {
        try {
            Class<? extends Event> evClass = Class.forName("com.vexsoftware.votifier.model.VotifierEvent").asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(evClass, new Listener() {}, EventPriority.MONITOR, (listener, event) -> {
                try {
                    Object vote = event.getClass().getMethod("getVote").invoke(event);
                    String name = (String) vote.getClass().getMethod("getUsername").invoke(vote);
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.tutorial().handleVote(name));
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Vote Votifier illisible : " + ex.getMessage());
                }
            }, plugin);
            plugin.getLogger().info("Votifier detecte : les votes valident la quete /vote.");
        } catch (ClassNotFoundException ignored) {
            plugin.getLogger().info("Votifier absent : utilise /tutoriel vote <joueur> depuis ton plugin de vote.");
        }
    }
}
