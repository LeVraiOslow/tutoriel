package fr.ivalona.tutoriel.cinematic;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Trajectoire automatique : spirale descendante autour d'un centre (le spawn par defaut),
 * camera toujours braquee sur le centre. Fluide grace a un lissage "smoothstep".
 */
public final class AutoOrbitPath implements CameraPath {
    private final World world;
    private final double cx, cy, cz;
    private final double r0, r1, h0, h1, turns, lookY, startAngle;
    private final int total;

    public AutoOrbitPath(World world, double cx, double cy, double cz, ConfigurationSection cfg) {
        this.world = world;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.total = Math.max(40, (int) Math.round((cfg == null ? 34 : cfg.getDouble("duration-seconds", 34)) * 20));
        this.r0 = cfg == null ? 70 : cfg.getDouble("radius-start", 70);
        this.r1 = cfg == null ? 14 : cfg.getDouble("radius-end", 14);
        this.h0 = cfg == null ? 65 : cfg.getDouble("height-start", 65);
        this.h1 = cfg == null ? 9 : cfg.getDouble("height-end", 9);
        this.turns = cfg == null ? 1.25 : cfg.getDouble("turns", 1.25);
        this.lookY = cfg == null ? 3 : cfg.getDouble("look-offset-y", 3);
        this.startAngle = cfg == null ? 0 : Math.toRadians(cfg.getDouble("start-angle", 0));
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public int totalTicks() {
        return total;
    }

    @Override
    public Frame at(double tick) {
        double e = CameraPath.smooth(tick / total);
        double ang = startAngle + turns * Math.PI * 2 * e;
        double r = CameraPath.lerp(r0, r1, e);
        double h = CameraPath.lerp(h0, h1, e);
        double x = cx + Math.cos(ang) * r;
        double z = cz + Math.sin(ang) * r;
        double y = cy + h;
        double dx = cx - x, dy = (cy + lookY) - y, dz = cz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new Frame(x, y, z, yaw, pitch);
    }
}
