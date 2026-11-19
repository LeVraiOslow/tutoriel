package fr.ivalona.tutoriel.cinematic;

import org.bukkit.World;

/** Trajectoire de la camera de la cinematique. */
public interface CameraPath {
    record Frame(double x, double y, double z, float yaw, float pitch) {}

    World world();

    int totalTicks();

    Frame at(double tick);

    static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
