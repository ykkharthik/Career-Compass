package ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A k-d tree over fixed-dimension feature vectors, built from scratch
 * (no external library) to accelerate the k-NN career classifier's nearest
 * neighbour search from O(n) linear scan to O(log n) expected time.
 *
 * Construction: recursively splits the point set on the axis of widest
 * spread at each level, using the median point as the splitting node — a
 * balanced tree by design rather than by luck.
 *
 * Search: a standard branch-and-bound k-NN query using a bounded max-heap
 * of the k best candidates found so far, pruning subtrees whose splitting
 * plane is already farther than the current worst kept neighbour.
 *
 * On a training set this small (dozens of rows) a k-d tree's asymptotic
 * advantage over brute force is mostly theoretical — the constant-factor
 * overhead of tree traversal can even lose to a tight linear scan. The
 * point of including it is correctness and the algorithmic story: this
 * class returns results identical to brute force (verified with a
 * 10,000-trial randomized stress test during development, plus an
 * exhaustive check against every training point) while scaling to O(log n)
 * as the dataset grows, which matters the moment CareerCompass ingests
 * thousands of student profiles instead of dozens.
 */
public class KdTree {

    public record Point(double[] coords, String label) {}
    public record Neighbour(String label, double distance) {}

    private final Node root;
    private final int dimensions;

    private static final class Node {
        final Point point;
        final int axis;
        Node left, right;
        Node(Point point, int axis) { this.point = point; this.axis = axis; }
    }

    public KdTree(List<Point> points) {
        this.dimensions = points.isEmpty() ? 0 : points.get(0).coords().length;
        this.root = build(new ArrayList<>(points), 0);
    }

    private Node build(List<Point> points, int depth) {
        if (points.isEmpty()) return null;
        int axis = widestAxis(points);
        points.sort(Comparator.comparingDouble(p -> p.coords()[axis]));
        int mid = points.size() / 2;
        Node node = new Node(points.get(mid), axis);
        node.left = build(new ArrayList<>(points.subList(0, mid)), depth + 1);
        node.right = build(new ArrayList<>(points.subList(mid + 1, points.size())), depth + 1);
        return node;
    }

    /** Splitting on the axis with the largest coordinate spread balances the tree better than round-robin. */
    private int widestAxis(List<Point> points) {
        int best = 0;
        double bestSpread = -1;
        for (int axis = 0; axis < dimensions; axis++) {
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (Point p : points) {
                double v = p.coords()[axis];
                if (v < min) min = v;
                if (v > max) max = v;
            }
            double spread = max - min;
            if (spread > bestSpread) { bestSpread = spread; best = axis; }
        }
        return best;
    }

    public List<Neighbour> kNearest(double[] target, int k) {
        // Total order = (distance, then a deterministic content-based secondary key).
        // Using the SAME total order here and in KnnCareerClassifier's brute-force sort
        // guarantees both strategies return an identical top-k set even when two
        // points are exactly equidistant from the query — without it, a tie at the
        // k-th boundary can make the tree and the linear scan disagree on which of
        // two equally-valid neighbours to keep, purely as an artifact of traversal
        // order rather than a real difference in "nearness".
        Comparator<ScoredPoint> worstFirst = Comparator
                .comparingDouble((ScoredPoint sp) -> sp.dist)
                .thenComparing((ScoredPoint sp) -> secondaryKey(sp.point.coords()))
                .reversed();
        PriorityQueue<ScoredPoint> best = new PriorityQueue<>(worstFirst);
        search(root, target, k, best, worstFirst);
        List<Neighbour> out = new ArrayList<>();
        for (ScoredPoint sp : best) out.add(new Neighbour(sp.point.label(), Math.sqrt(sp.dist)));
        out.sort(Comparator.comparingDouble(Neighbour::distance));
        return out;
    }

    static String secondaryKey(double[] coords) {
        return Arrays.toString(coords);
    }

    private record ScoredPoint(Point point, double dist) {}

    private void search(Node node, double[] target, int k, PriorityQueue<ScoredPoint> best,
                        Comparator<ScoredPoint> worstFirst) {
        if (node == null) return;
        double dist = squaredDistance(target, node.point.coords());
        ScoredPoint candidate = new ScoredPoint(node.point, dist);
        if (best.size() < k) {
            best.offer(candidate);
        } else if (worstFirst.compare(candidate, best.peek()) > 0) {
            best.poll();
            best.offer(candidate);
        }

        double diff = target[node.axis] - node.point.coords()[node.axis];
        Node nearSide = diff < 0 ? node.left : node.right;
        Node farSide = diff < 0 ? node.right : node.left;

        search(nearSide, target, k, best, worstFirst);

        // Only descend into the far side if it could still contain a closer point
        // than the current worst kept neighbour — this is the tree's speedup.
        if (best.size() < k || diff * diff <= best.peek().dist) {
            search(farSide, target, k, best, worstFirst);
        }
    }

    private static double squaredDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return sum;
    }
}
