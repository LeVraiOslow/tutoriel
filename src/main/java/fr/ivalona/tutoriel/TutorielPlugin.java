package fr.ivalona.tutoriel;

import fr.ivalona.tutoriel.cinematic.CinematicManager;
import fr.ivalona.tutoriel.command.TutorielCommand;
import fr.ivalona.tutoriel.data.DataManager;
import fr.ivalona.tutoriel.hook.TutorielExpansion;
import fr.ivalona.tutoriel.hook.VoteHook;
import fr.ivalona.tutoriel.listener.TutorialListener;
import fr.ivalona.tutoriel.tutorial.TutorialManager;
import fr.ivalona.tutoriel.util.Messages;
import fr.ivalona.tutoriel.util.Text;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class TutorielPlugin extends JavaPlugin {
    private Messages messages;
    private DataManager data;
    private TutorialManager tutorial;
    private CinematicManager cinematic;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        Text.init(this);
        data = new DataManager(this);
        tutorial = new TutorialManager(this);
        cinematic = new CinematicManager(this);
        tutorial.load();
        cinematic.reload();

        getServer().getPluginManager().registerEvents(new TutorialListener(this), this);

        PluginCommand cmd = getCommand("tutoriel");
        if (cmd != null) {
            TutorielCommand ex = new TutorielCommand(this);
            cmd.setExecutor(ex);
            cmd.setTabCompleter(ex);
        }

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new TutorielExpansion(this).register();
            getLogger().info("Placeholders %tutoriel_...% enregistres (PlaceholderAPI).");
        }
        VoteHook.register(this);

        if (economy() == null) {
            getLogger().warning("Aucune economie Vault detectee : les recompenses ne seront pas versees tant qu'elle n'est pas disponible.");
        }
        getServer().getScheduler().runTaskTimer(this, data::flush, 600L, 600L);
    }

    @Override
    public void onDisable() {
        if (cinematic != null) cinematic.shutdown();
        if (tutorial != null) tutorial.hideAllBars();
        if (data != null) data.flushNow();
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        tutorial.load();
        cinematic.reload();
    }

    /** Economie Vault (resolue a la demande : Essentials l'enregistre parfois apres nous). */
    public Economy economy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }

    public Messages messages() {
        return messages;
    }

    public DataManager data() {
        return data;
    }

    public TutorialManager tutorial() {
        return tutorial;
    }

    public CinematicManager cinematic() {
        return cinematic;
    }
}
