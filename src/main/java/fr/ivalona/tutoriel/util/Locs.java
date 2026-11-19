package fr.ivalona.tutoriel.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Serialisation simple d'une position : monde,x,y,z,yaw,pitch */
public final class Locs {
    private Locs() {}

    public static String format(Location l) {
        return l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw() + "," + l.getPitch();
    }

    public static Location parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String[] a = s.split(",");
            World w = Bukkit.getWorld(a[0].trim());
            if (w == null) return null;
            return new Location(w,
                    Double.parseDouble(a[1].trim()), Double.parseDouble(a[2].trim()), Double.parseDouble(a[3].trim()),
                    a.length > 4 ? Float.parseFloat(a[4].trim()) : 0f,
                    a.length > 5 ? Float.parseFloat(a[5].trim()) : 0f);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
