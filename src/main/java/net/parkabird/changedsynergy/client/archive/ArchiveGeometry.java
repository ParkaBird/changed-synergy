package net.parkabird.changedsynergy.client.archive;

import java.util.ArrayList;

/** Paper-space transforms from the supplied Godot archive, independent of the renderer. */
public final class ArchiveGeometry {
    private ArchiveGeometry() {}
    public static final double WIDTH = 1920, HEIGHT = 1080, PAPER_W = 774, PAPER_H = 774.0 * 297 / 210;
    public record Point(double x, double y) {}
    public record Fit(double scale, double x, double y) {
        public Point logical(double screenX, double screenY) {
            return new Point((screenX - x) / scale, (screenY - y) / scale);
        }
        public Point screen(Point p) { return new Point(x + p.x * scale, y + p.y * scale); }
    }
    public static Fit fit(double guiWidth, double guiHeight) {
        if (guiWidth <= 0 || guiHeight <= 0) throw new IllegalArgumentException("Empty GUI");
        double scale = Math.min(guiWidth / WIDTH, guiHeight / HEIGHT);
        return new Fit(scale, (guiWidth - WIDTH * scale) / 2, (guiHeight - HEIGHT * scale) / 2);
    }
    public static double smooth(double value) {
        double u = Math.max(0, Math.min(1, value));
        return u * u * u * (u * (6 * u - 15) + 10);
    }
    /** Pose center is the right A4 leaf center, rather than the spread center. */
    public static Point paperToCanvas(Point p, Point center, double angle, double scale) {
        double x = (p.x - PAPER_W / 2) * scale, y = (p.y - PAPER_H / 2) * scale;
        return new Point(center.x + Math.cos(angle) * x - Math.sin(angle) * y,
                center.y + Math.sin(angle) * x + Math.cos(angle) * y);
    }
    public static Point canvasToPaper(Point p, Point center, double angle, double scale) {
        if (scale <= 0) throw new IllegalArgumentException("Invalid paper scale");
        double x = p.x - center.x, y = p.y - center.y;
        return new Point((Math.cos(angle) * x + Math.sin(angle) * y) / scale + PAPER_W / 2,
                (-Math.sin(angle) * x + Math.cos(angle) * y) / scale + PAPER_H / 2);
    }
    public static Point fan(Point p, int side, int depth, double weight) {
        double angle = Math.toRadians(depth * 2 * weight), y = p.y - PAPER_H / 2;
        return new Point(side * (Math.cos(angle) * p.x - Math.sin(angle) * y),
                Math.sin(angle) * p.x + Math.cos(angle) * y + PAPER_H / 2 + depth * 1.5);
    }
    public static Point[] stackLeaf(int side, int depth, double weight) {
        return new Point[]{fan(new Point(0, 0), side, depth, weight),
                fan(new Point(PAPER_W, 0), side, depth, weight),
                fan(new Point(PAPER_W, PAPER_H), side, depth, weight),
                fan(new Point(0, PAPER_H), side, depth, weight)};
    }
    /** Only the physical band below the preceding sheet is interactive, as in the showroom. */
    public static Point[] stackEdge(int side, int depth, double weight) {
        Point start = fan(new Point(0, PAPER_H), side, depth - 1, weight);
        Point end = fan(new Point(PAPER_W, PAPER_H), side, depth - 1, weight);
        double dx = end.x - start.x, dy = end.y - start.y, length = Math.hypot(dx, dy);
        dx /= length; dy /= length;
        Point[] band = {fan(new Point(PAPER_W - 180, PAPER_H - 32), side, depth, weight),
                fan(new Point(PAPER_W, PAPER_H - 32), side, depth, weight),
                fan(new Point(PAPER_W, PAPER_H - .5), side, depth, weight),
                fan(new Point(PAPER_W - 180, PAPER_H - .5), side, depth, weight)};
        ArrayList<Point> exposed = new ArrayList<>(6);
        Point last = band[band.length - 1];
        double lastDistance = (dx * (last.y - start.y) - dy * (last.x - start.x)) * side - .5;
        for (Point point : band) {
            double distance = (dx * (point.y - start.y) - dy * (point.x - start.x)) * side - .5;
            if ((distance >= 0) != (lastDistance >= 0)) {
                double t = lastDistance / (lastDistance - distance);
                exposed.add(new Point(last.x + (point.x - last.x) * t, last.y + (point.y - last.y) * t));
            }
            if (distance >= 0) exposed.add(point);
            last = point; lastDistance = distance;
        }
        return exposed.toArray(Point[]::new);
    }
    /** -1 means no exposed page. Detail sheets return their roster's current page. */
    public static int stackPageAt(Point point, int page, int pageCount, boolean detail,
                                  double fanLeft, double fanRight) {
        for (int side : new int[]{-1, 1}) {
            double weight = side < 0 ? fanLeft : fanRight;
            if (weight <= .35) continue;
            int available = detail ? (side < 0 ? 1 : 0) : side < 0 ? page : pageCount - page - 1;
            // Shallower paper is rendered last, and therefore receives a shared edge first.
            for (int depth = 1; depth <= Math.min(3, available); depth++)
                if (contains(stackEdge(side, depth, weight), point)) return detail ? page : page + side * depth;
        }
        return -1;
    }
    /** Same fixed-spine curl as the original shader; progress is already eased. */
    public static Point curl(double x, double normalizedY, double meshWidth, double progress, int direction) {
        double sine = Math.sin(progress * Math.PI);
        double radius = Math.max(.1, meshWidth * .075 * sine);
        double crease = meshWidth * (1 - progress) + meshWidth * .08 * sine * (.5 - normalizedY);
        double distance = Math.max(0, x - crease), angle = Math.min(distance / radius, Math.PI);
        double turned = crease + radius * Math.sin(angle) - Math.max(0, distance - radius * Math.PI);
        return new Point(direction * (distance > 0 ? turned : x),
                normalizedY * PAPER_H + Math.sin(angle) * radius * .065 * (normalizedY - .5));
    }
    public static boolean contains(Point[] polygon, Point p) {
        if (polygon.length < 3) return false;
        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            Point a = polygon[i], b = polygon[j];
            double cross = (p.x - a.x) * (b.y - a.y) - (p.y - a.y) * (b.x - a.x);
            if (Math.abs(cross) < 1e-8 && p.x >= Math.min(a.x, b.x) - 1e-8
                    && p.x <= Math.max(a.x, b.x) + 1e-8 && p.y >= Math.min(a.y, b.y) - 1e-8
                    && p.y <= Math.max(a.y, b.y) + 1e-8) return true;
            if ((a.y > p.y) != (b.y > p.y)
                    && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside;
        }
        return inside;
    }
}
