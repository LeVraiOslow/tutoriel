package fr.ivalona.tutoriel.cinematic;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/** Trajectoire passant par des points de passage (Catmull-Rom), definis avec /tutoriel cinematic addpoint. */
public final class SplinePath implements CameraPath {
    public record Point(double x, double y, double z, double yaw, double pitch, int ticks) {}

    private final World world;
    private final List<Point> pts = new ArrayList<>();
    private final int[] cum;
    private final int total;

    public SplinePath(World world, List<Point> in) {
        this.world = world;
        double prev = in.get(0).yaw();
        for (int i = 0; i < in.size(); i++) {
            Point p = in.get(i);
            double y = p.yaw();
            if (i > 0) {
                while (y - prev > 180) y -= 360;
                while (y - prev < -180) y += 360;
            }
            prev = y;
            pts.add(new Point(p.x(), p.y(), p.z(), y, p.pitch(), p.ticks()));
        }
        cum = new int[pts.size()];
        for (int i = 1; i < cum.length; i++) cum[i] = cum[i - 1] + Math.max(1, pts.get(i).ticks());
        total = cum[cum.length - 1];
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
        int n = pts.size();
        if (tick >= total) {
            Point l = pts.get(n - 1);
            return new Frame(l.x(), l.y(), l.z(), (float) l.yaw(), (float) l.pitch());
        }
        int i = 0;
        while (i < n - 2 && tick >= cum[i + 1]) i++;
        double u = (tick - cum[i]) / Math.max(1, cum[i + 1] - cum[i]);
        Point p0 = pts.get(Math.max(i - 1, 0));
        Point p1 = pts.get(i);
        Point p2 = pts.get(i + 1);
        Point p3 = pts.get(Math.min(i + 2, n - 1));
        return new Frame(
                cr(p0.x(), p1.x(), p2.x(), p3.x(), u),
                cr(p0.y(), p1.y(), p2.y(), p3.y(), u),
                cr(p0.z(), p1.z(), p2.z(), p3.z(), u),
                (float) CameraPath.lerp(p1.yaw(), p2.yaw(), u),
                (float) CameraPath.lerp(p1.pitch(), p2.pitch(), u));
    }

    private static double cr(double a, double b, double c, double d, double t) {
        return 0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (-a + 3 * b - 3 * c + d) * t * t * t);
    }
}
