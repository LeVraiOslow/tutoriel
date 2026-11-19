package fr.ivalona.tutoriel.data;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * step : 0 = tutoriel pas commence, 1..N = quete en cours, N+1 = termine.
 */
public final class PlayerData {
    public int step;
    public boolean cinematicDone;
    public final Set<String> flags = new HashSet<>();
    public String restoreGameMode;
    public String restoreLocation;

    static PlayerData from(ConfigurationSection s) {
        PlayerData d = new PlayerData();
        d.step = s.getInt("step", 0);
        d.cinematicDone = s.getBoolean("cinematic-done", false);
        d.flags.addAll(s.getStringList("flags"));
        d.restoreGameMode = s.getString("restore-gamemode");
        d.restoreLocation = s.getString("restore-location");
        return d;
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("step", step);
        m.put("cinematic-done", cinematicDone);
        m.put("flags", new java.util.ArrayList<>(flags));
        if (restoreGameMode != null) m.put("restore-gamemode", restoreGameMode);
        if (restoreLocation != null) m.put("restore-location", restoreLocation);
        return m;
    }
}
