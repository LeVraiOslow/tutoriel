package fr.ivalona.tutoriel.util;

import fr.ivalona.tutoriel.TutorielPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Acces a messages.yml : textes simples, listes et "bundles" (chat + titre + actionbar + son). */
public final class Messages {
    private final TutorielPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public Messages(TutorielPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public String str(String path) {
        return yaml.getString(path, "");
    }

    private Map<String, String> withPrefix(Map<String, String> vars) {
        Map<String, String> v = new HashMap<>();
        v.put("prefix", yaml.getString("prefix", ""));
        if (vars != null) v.putAll(vars);
        return v;
    }

    /** Envoie un message (texte ou liste de lignes) a un joueur ou a la console. */
    public void send(CommandSender to, String path, Map<String, String> vars) {
        if (!yaml.contains(path)) return;
        Map<String, String> v = withPrefix(vars);
        Player viewer = to instanceof Player pl ? pl : null;
        List<String> lines = yaml.isList(path) ? yaml.getStringList(path) : List.of(yaml.getString(path, ""));
        for (String l : lines) {
            to.sendMessage(Text.parse(l, viewer, v));
        }
    }

    /** Bundle : chat (liste), title (section), actionbar, sound. */
    public void bundle(Player p, String path, Map<String, String> vars) {
        ConfigurationSection s = yaml.getConfigurationSection(path);
        if (s == null) return;
        Map<String, String> v = withPrefix(vars);
        for (String line : s.getStringList("chat")) {
            p.sendMessage(Text.parse(line, p, v));
        }
        Fx.title(p, s.getConfigurationSection("title"), v);
        String ab = s.getString("actionbar", "");
        if (!ab.isEmpty()) p.sendActionBar(Text.parse(ab, p, v));
        Fx.sound(p, s.getString("sound", ""));
    }
}
