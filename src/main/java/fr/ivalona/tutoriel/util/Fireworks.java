package fr.ivalona.tutoriel.util;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Feux d'artifice qui explosent instantanement en l'air. */
public final class Fireworks {
    private static final Color[] PALETTE = {Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.BLUE, Color.FUCHSIA, Color.WHITE};

    private Fireworks() {}

    public static void burst(JavaPlugin plugin, Location center, int count, double radius, double height, List<String> colorNames) {
        World w = center.getWorld();
        if (w == null) return;
        List<Color> colors = new ArrayList<>();
        if (colorNames != null) {
            for (String n : colorNames) {
                Color c = color(n);
                if (c != null) colors.add(c);
            }
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(r.nextDouble()) * radius;
            double x = center.getX() + Math.cos(a) * d;
            double z = center.getZ() + Math.sin(a) * d;
            double y = w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1 + height * (0.6 + r.nextDouble() * 0.8);
            Location loc = new Location(w, x, y, z);
            Bukkit.getScheduler().runTaskLater(plugin, () -> spawn(plugin, loc, colors), i * 3L);
        }
    }

    private static void spawn(JavaPlugin plugin, Location loc, List<Color> colors) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Firework fw = loc.getWorld().spawn(loc, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            FireworkEffect.Type[] types = FireworkEffect.Type.values();
            FireworkEffect.Builder b = FireworkEffect.builder()
                    .with(types[r.nextInt(types.length)])
                    .flicker(true)
                    .trail(true);
            if (colors.isEmpty()) {
                b.withColor(PALETTE[r.nextInt(PALETTE.length)]);
                b.withFade(PALETTE[r.nextInt(PALETTE.length)]);
            } else {
                b.withColor(colors.get(r.nextInt(colors.size())));
                b.withFade(colors.get(r.nextInt(colors.size())));
            }
            meta.addEffect(b.build());
            meta.setPower(0);
            f.setFireworkMeta(meta);
        });
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 1L);
    }

    private static Color color(String n) {
        if (n == null) return null;
        String s = n.trim();
        if (s.startsWith("#") && s.length() == 7) {
            try {
                return Color.fromRGB(Integer.parseInt(s.substring(1), 16));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return switch (s.toUpperCase(Locale.ROOT)) {
            case "RED" -> Color.RED;
            case "ORANGE" -> Color.ORANGE;
            case "YELLOW" -> Color.YELLOW;
            case "GOLD" -> Color.fromRGB(255, 170, 0);
            case "LIME" -> Color.LIME;
            case "GREEN" -> Color.GREEN;
            case "AQUA" -> Color.AQUA;
            case "TEAL" -> Color.TEAL;
            case "BLUE" -> Color.BLUE;
            case "PURPLE" -> Color.PURPLE;
            case "FUCHSIA", "PINK" -> Color.FUCHSIA;
            case "WHITE" -> Color.WHITE;
            case "SILVER" -> Color.SILVER;
            default -> null;
        };
    }
}
