package web;

import auth.User;
import com.sun.net.httpserver.HttpExchange;
import ml.KnnCareerClassifier;
import ml.NaiveBayesClassifier;

import java.io.IOException;
import java.util.List;

/**
 * A live demonstration of the ML engineering behind the recommendation
 * engine, in two parts:
 *
 * <p><b>Two learning paradigms</b> — {@link KnnCareerClassifier} (instance-
 * based: compare to the closest labelled examples) and
 * {@link NaiveBayesClassifier} (generative: fit a distribution per class and
 * ask which explains the profile best) are compared on accuracy via
 * leave-one-out cross-validation, not just asserted to both be reasonable.
 *
 * <p><b>Two lookup strategies</b> — within k-NN specifically, a brute-force
 * linear scan (what actually serves predictions) is compared against the
 * from-scratch k-d tree for correctness and timing, re-running on every
 * request the same kind of check the README describes running offline.
 */
public final class BenchmarkPages {

    private final KnnCareerClassifier classifier;
    private final NaiveBayesClassifier naiveBayes;
    private final AppContext ctx;

    public BenchmarkPages(KnnCareerClassifier classifier, NaiveBayesClassifier naiveBayes, AppContext ctx) {
        this.classifier = classifier;
        this.naiveBayes = naiveBayes;
        this.ctx = ctx;
    }

    public void page(HttpExchange ex) throws IOException {
        User u = ctx.currentUser(ex);
        if (u == null) { Http.redirect(ex, "/"); return; }

        List<KnnCareerClassifier.Example> examples = classifier.examples();
        int n = examples.size();
        int k = classifier.k();

        StringBuilder b = new StringBuilder();
        b.append("<h1>ML engine benchmark</h1><p class=\"sub\">")
                .append("Two things the recommendation engine claims are usually taken on faith in a student ")
                .append("project: that the two learning models actually agree with reality, and that the ")
                .append("faster data structure actually gives the same answer as the simple one. Both are ")
                .append("checked live below, on every request, instead of just in a README paragraph.</p>");

        appendAccuracySection(b, examples, n);
        appendCorrectnessSection(b, examples, n);
        appendTimingSection(b, examples, n, k);

        Http.html(ex, 200, Pages.shell("ML Benchmark", ctx.navFor(u), b.toString()));
    }

    /** k-NN vs Naive Bayes: leave-one-out cross-validation accuracy — a real comparison, not a coin flip. */
    private void appendAccuracySection(StringBuilder b, List<KnnCareerClassifier.Example> examples, int n) {
        double knnAcc = classifier.leaveOneOutAccuracy();
        double nbAcc = naiveBayes.leaveOneOutAccuracy();
        int knnPct = (int) Math.round(knnAcc * 100);
        int nbPct = (int) Math.round(nbAcc * 100);

        b.append("<h2>Two learning paradigms</h2>")
                .append("<p class=\"sub\">Leave-one-out cross-validation over all ").append(n)
                .append(" training examples: each point is held out, the model refits on the rest, and is ")
                .append("checked against the held-out point's true label — the same discipline as a real ")
                .append("accuracy evaluation, not just fitting and grading your own homework.</p>")
                .append("<div class=\"card\">")
                .append(Pages.gauge("k-NN (instance-based)", knnPct + "% accuracy", knnPct, null))
                .append(Pages.gauge("Naive Bayes (generative)", nbPct + "% accuracy", nbPct, null))
                .append("<p class=\"sub\" style=\"margin-top:1rem;margin-bottom:0\">Recommendations blend both ")
                .append("(25% each) alongside the transparent rule engine (50%) — two different models landing ")
                .append("on the same domain is stronger evidence than either alone.</p>")
                .append("</div>");
    }

    /** Does the k-d tree agree with brute force on every training point, used as a query? */
    private void appendCorrectnessSection(StringBuilder b, List<KnnCareerClassifier.Example> examples, int n) {
        int mismatches = 0;
        for (var example : examples) {
            var bruteForce = classifier.nearestNeighbours(example.features());
            var kdTree = classifier.kdNearestNeighbours(example.features());
            if (!sameLabels(bruteForce, kdTree)) mismatches++;
        }

        b.append("<h2>Two lookup strategies for k-NN</h2>")
                .append("<div class=\"card").append(mismatches == 0 ? " lead" : "").append("\">")
                .append("<h3 style=\"margin-top:0\">Correctness</h3>");
        if (mismatches == 0) {
            b.append(Pages.noteBox("<b>0 mismatches</b> — the k-d tree agrees with brute force on all " + n
                    + " training points as queries, matching the wider offline verification described in the "
                    + "README (10,000 randomized trials, 5 edge cases, 6 values of k)."));
        } else {
            b.append(Pages.errorBox(mismatches + " of " + n + " queries disagree between strategies — "
                    + "see KdTree's tie-break comparator."));
        }
        b.append("</div>");
    }

    /** Honest timing: brute force vs k-d tree, including the case where the tree doesn't win. */
    private void appendTimingSection(StringBuilder b, List<KnnCareerClassifier.Example> examples, int n, int k) {
        int warmup = 300, measured = 2000;
        for (int i = 0; i < warmup; i++)
            for (var e : examples) { classifier.nearestNeighbours(e.features()); classifier.kdNearestNeighbours(e.features()); }

        long bruteForceNanos = 0;
        for (int i = 0; i < measured; i++)
            for (var e : examples) {
                long t0 = System.nanoTime();
                classifier.nearestNeighbours(e.features());
                bruteForceNanos += System.nanoTime() - t0;
            }
        long kdTreeNanos = 0;
        for (int i = 0; i < measured; i++)
            for (var e : examples) {
                long t0 = System.nanoTime();
                classifier.kdNearestNeighbours(e.features());
                kdTreeNanos += System.nanoTime() - t0;
            }

        long totalQueries = (long) measured * n;
        double bruteForceUs = bruteForceNanos / 1000.0 / totalQueries;
        double kdTreeUs = kdTreeNanos / 1000.0 / totalQueries;
        double maxUs = Math.max(bruteForceUs, kdTreeUs);
        int bruteForcePct = (int) Math.round(100 * bruteForceUs / maxUs);
        int kdTreePct = (int) Math.round(100 * kdTreeUs / maxUs);

        b.append("<div class=\"card\"><h3 style=\"margin-top:0\">Timing — average per query (k=").append(k).append(")</h3>")
                .append("<p class=\"sub\">").append(n).append(" training rows is small enough that a linear scan ")
                .append("is already fast; the k-d tree's advantage grows as the training set scales past a few ")
                .append("dozen rows, which is exactly why the shipped predictions use brute force and the README ")
                .append("doesn't claim a win here.</p>")
                .append(Pages.gauge("Brute-force scan", String.format("%.2f µs", bruteForceUs), bruteForcePct, null))
                .append(Pages.gauge("k-d tree", String.format("%.2f µs", kdTreeUs), kdTreePct, null))
                .append("</div>");
    }

    /** Same total order both strategies should reach — see KdTree's deterministic tie-break. */
    private static boolean sameLabels(List<KnnCareerClassifier.Neighbour> a, List<KnnCareerClassifier.Neighbour> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (!a.get(i).label().equals(b.get(i).label())) return false;
        return true;
    }
}
