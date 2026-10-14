package net.parkabird.changedsynergy.client.archive;

import static net.parkabird.changedsynergy.client.archive.ArchiveGeometry.*;

/** Runnable without Minecraft: the migrated transforms keep hit testing and curled sheets aligned. */
public final class ArchiveGeometryCheck {
    private static int checks;
    private static void close(double a, double b) {
        checks++;
        if (Math.abs(a - b) > 1e-7) throw new AssertionError(a + " != " + b);
    }
    public static void main(String[] args) {
        for (double[] size : new double[][]{{1280,720},{960,540},{854,480},{1920,1200},{3440,1440}}) {
            Fit fit = fit(size[0], size[1]);
            Point original = new Point(960,632), screen = fit.screen(original);
            Point roundtrip = fit.logical(screen.x(), screen.y());
            close(roundtrip.x(), original.x()); close(roundtrip.y(), original.y());
        }
        for (double angle : new double[]{-.0872664626,0,.1570796327}) {
            Point original = new Point(-774,1094), center = new Point(960,632);
            Point roundtrip = canvasToPaper(paperToCanvas(original, center, angle, .45), center, angle, .45);
            close(roundtrip.x(), original.x()); close(roundtrip.y(), original.y());
        }
        close(smooth(-1),0); close(smooth(.5),.5); close(smooth(2),1);
        for (int direction : new int[]{-1,1}) for (double progress : new double[]{0,.25,.5,.75,1}) {
            close(curl(0,.5,774,progress,direction).x(),0);
            close(curl(774,.5,774,0,direction).x(),774*direction);
            close(curl(774,.5,774,1,direction).x(),-(774-Math.PI*.1)*direction);
            for (double y : new double[]{0,.5,1}) {
                Point point = curl(832,y,832,progress,direction);
                if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) throw new AssertionError("Invalid divider curl");
                checks++;
            }
        }
        Point right = fan(new Point(400,600),1,2,1), left = fan(new Point(400,600),-1,2,1);
        close(right.x(),-left.x()); close(right.y(),left.y());
        Point[] quad = {new Point(0,0),new Point(100,10),new Point(90,30),new Point(0,20)};
        close(contains(quad,new Point(50,15))?1:0,1);
        close(contains(quad,new Point(99,29))?1:0,0);
        close(contains(quad,new Point(0,0))?1:0,1);
        close(contains(new Point[0],new Point(0,0))?1:0,0);
        for (int side : new int[]{-1, 1}) for (int depth = 1; depth <= 3; depth++) {
            Point[] edge = stackEdge(side, depth, 1);
            if (edge.length < 3) throw new AssertionError("Missing exposed sheet edge");
            double x = 0, y = 0;
            for (Point p : edge) { x += p.x(); y += p.y(); }
            Point center = new Point(x / edge.length, y / edge.length);
            close(stackPageAt(center,3,7,false,1,1),3 + side * depth);
            close(stackPageAt(center,3,7,false,0,0),-1);
            if (depth == 1 && side < 0) close(stackPageAt(center,3,7,true,1,1),3);
            if (side > 0) close(stackPageAt(center,3,7,true,1,1),-1);
            Point[] mirror = stackEdge(-side, depth, 1);
            close(edge.length,mirror.length);
            for (int i = 0; i < edge.length; i++) {
                close(edge[i].x(),-mirror[i].x()); close(edge[i].y(),mirror[i].y());
            }
        }
        System.out.println("ARCHIVE_GEOMETRY_PASS: " + checks + " checks");
    }
}
