package web;

/**
 * Builds a small inline SVG radar chart of a student's five interest
 * ratings — hand-drawn geometry, no charting library. Kept separate from
 * Pages.java because it's pure computation (trig placement of vertices)
 * rather than markup.
 */
public final class SvgCharts {

    private SvgCharts() {}

    private static final String[] AXIS_LABELS = {"Coding", "Maths", "Design", "Comms", "Security"};

    /** values in 1..5 order: coding, math, design, communication, security. */
    public static String radar(int[] values) {
        double cx = 140, cy = 128, maxR = 70;
        int n = 5;
        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 280 260\" xmlns=\"http://www.w3.org/2000/svg\" ")
           .append("role=\"img\" aria-label=\"Interest profile radar chart\">");

        // background rings at 1..5
        for (int ring = 1; ring <= 5; ring++) {
            double r = maxR * ring / 5.0;
            svg.append("<polygon points=\"").append(polygonPoints(cx, cy, r, n))
               .append("\" fill=\"none\" stroke=\"#CBD8D4\" stroke-width=\"1\"/>");
        }
        // spokes + axis labels
        for (int i = 0; i < n; i++) {
            double angle = angleFor(i, n);
            double x = cx + maxR * Math.cos(angle);
            double y = cy + maxR * Math.sin(angle);
            svg.append("<line x1=\"").append(fmt(cx)).append("\" y1=\"").append(fmt(cy))
               .append("\" x2=\"").append(fmt(x)).append("\" y2=\"").append(fmt(y))
               .append("\" stroke=\"#CBD8D4\" stroke-width=\"1\"/>");
            double cosA = Math.cos(angle);
            double lx = cx + (maxR + 16) * cosA;
            double ly = cy + (maxR + 16) * Math.sin(angle);
            String anchor = cosA > 0.35 ? "start" : cosA < -0.35 ? "end" : "middle";
            svg.append("<text x=\"").append(fmt(lx)).append("\" y=\"").append(fmt(ly))
               .append("\" font-family=\"IBM Plex Mono,monospace\" font-size=\"10\" fill=\"#59707D\" ")
               .append("text-anchor=\"").append(anchor).append("\" dominant-baseline=\"middle\">")
               .append(AXIS_LABELS[i]).append("</text>");
        }
        // data polygon
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double angle = angleFor(i, n);
            double r = maxR * Math.max(0, Math.min(5, values[i])) / 5.0;
            double x = cx + r * Math.cos(angle);
            double y = cy + r * Math.sin(angle);
            if (i > 0) pts.append(" ");
            pts.append(fmt(x)).append(",").append(fmt(y));
        }
        svg.append("<polygon points=\"").append(pts)
           .append("\" fill=\"#C2185B\" fill-opacity=\"0.16\" stroke=\"#C2185B\" stroke-width=\"2\"/>");
        // vertex dots
        for (int i = 0; i < n; i++) {
            double angle = angleFor(i, n);
            double r = maxR * Math.max(0, Math.min(5, values[i])) / 5.0;
            double x = cx + r * Math.cos(angle);
            double y = cy + r * Math.sin(angle);
            svg.append("<circle cx=\"").append(fmt(x)).append("\" cy=\"").append(fmt(y))
               .append("\" r=\"3\" fill=\"#C2185B\"/>");
        }
        svg.append("</svg>");
        return svg.toString();
    }

    private static double angleFor(int i, int n) {
        return -Math.PI / 2 + i * (2 * Math.PI / n);
    }

    private static String polygonPoints(double cx, double cy, double r, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double angle = angleFor(i, n);
            double x = cx + r * Math.cos(angle);
            double y = cy + r * Math.sin(angle);
            if (i > 0) sb.append(" ");
            sb.append(fmt(x)).append(",").append(fmt(y));
        }
        return sb.toString();
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }
}
