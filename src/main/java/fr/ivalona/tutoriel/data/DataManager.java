package fr.ivalona.tutoriel.data;

import fr.ivalona.tutoriel.TutorielPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stockage de la progression dans data.yml (ecriture differee et asynchrone). */
public final class DataManager {
    private final TutorielPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, PlayerData> cache = new HashMap<>();
    private boolean dirty;

    public DataManager(TutorielPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    /** Retourne null si le joueur n'a pas de donnees (ne participe pas au tutoriel). */
    public PlayerData get(UUID id) {
        PlayerData d = cache.get(id);
        if (d != null) return d;
        ConfigurationSection s = yaml.getConfigurationSection("players." + id);
        if (s == null) return null;
        d = PlayerData.from(s);
        cache.put(id, d);
        return d;
    }

    public PlayerData create(UUID id) {
        PlayerData d = new PlayerData();
        cache.put(id, d);
        save(id, d);
        return d;
    }

    public void save(UUID id, PlayerData d) {
        cache.put(id, d);
        yaml.set("players." + id, d.toMap());
        dirty = true;
    }

    public void flush() {
        if (!dirty) return;
        dirty = false;
        String content = yaml.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(content));
    }

    public void flushNow() {
        dirty = false;
        write(yaml.saveToString());
    }

    private synchronized void write(String content) {
        try {
            plugin.getDataFolder().mkdirs();
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'ecrire data.yml : " + e.getMessage());
        }
    }
}
