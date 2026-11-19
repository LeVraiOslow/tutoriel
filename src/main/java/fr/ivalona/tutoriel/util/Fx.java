package fr.ivalona.tutoriel.util;

import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;

/** Titres et sons configurables. */
public final class Fx {
    private Fx() {}

    private static Duration ticks(int t) {
        return Duration.ofMillis(Math.max(0, t) * 50L);
    }

    public static void title(Player p, ConfigurationSection s, Map<String, String> vars) {
        if (s == null) return;
        Title t = Title.title(
                Text.parse(s.getString("title", ""), p, vars),
                Text.parse(s.getString("subtitle", ""), p, vars),
                Title.Times.times(ticks(s.getInt("fade-in", 10)), ticks(s.getInt("stay", 60)), ticks(s.getInt("fade-out", 10))));
        p.showTitle(t);
    }

    /** Format : "cle;volume;pitch" (ex: minecraft:ui.toast.challenge_complete;1.0;1.0 ou ivalona:musique.intro). */
    public static void sound(Player p, String spec) {
        sound(p, spec, p.getLocation());
    }

    public static void sound(Player p, String spec, Location at) {
        if (spec == null || spec.isBlank()) return;
        String[] a = spec.split(";");
        float vol = a.length > 1 ? parse(a[1], 1f) : 1f;
        float pitch = a.length > 2 ? parse(a[2], 1f) : 1f;
        p.playSound(at, a[0].trim(), SoundCategory.MASTER, vol, pitch);
    }

    private static float parse(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
