package ml;

import repository.FileManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * k-Nearest-Neighbours career classifier implemented in pure Java
 * (no external ML libraries).
 *
 * Training data: data/career_training.csv with columns
 *   coding,math,design,communication,security,cgpa_scaled,label
 * Each row is a labelled example profile: interest ratings (1-5), CGPA scaled
 * to 0-5, and the career domain that fits that profile.
 *
 * Prediction: for a new student's feature vector, find the k closest training
 * rows by Euclidean distance and take a majority vote. Because every step is
 * visible (the neighbours themselves can be printed), the model stays fully
 * explainable — the same argument the rule engine makes, extended with a
 * learning component.
 *
 * Two lookup strategies are kept side by side on purpose: a brute-force
 * linear scan (used for the actual predictions, since it is trivially
 * correct) and a from-scratch {@link KdTree} offering the same query in
 * expected O(log n) instead of brute force's O(n) — reusable by any future
 * caller that needs faster nearest-neighbour lookups as the training set
 * grows well past a few dozen rows.
 */
public class KnnCareerClassifier {

    /** One labelled training example. */
    public record Example(double[] features, String label) {}

    /** A neighbour with its distance, returned for explainability. */
    public record Neighbour(String label, double distance) {}

    private final List<Example> trainingData = new ArrayList<>();
    private final int k;
    private final KdTree kdTree;

    public KnnCareerClassifier(String trainingCsvPath, int k) {
        this.k = k;
        for (String line : FileManager.readLines(trainingCsvPath)) {
            String[] p = line.split(",", -1);
            if (p.length < 7) continue;
            try {
                double[] f = new double[6];
                for (int i = 0; i < 6; i++) f[i] = Double.parseDouble(p[i]);
                trainingData.add(new Example(f, p[6].trim()));
            } catch (NumberFormatException ignored) {
                // header row or bad line
            }
        }
        List<KdTree.Point> points = new ArrayList<>();
        for (Example e : trainingData) points.add(new KdTree.Point(e.features(), e.label()));
        this.kdTree = new KdTree(points);
    }

    public boolean isReady() {
        return trainingData.size() >= k;
    }

    public int trainingSize() {
        return trainingData.size();
    }

    public List<Example> examples() {
        return new ArrayList<>(trainingData);
    }

    public int k() {
        return k;
    }

    /** Brute-force linear scan — used for the actual predictions. */
    public List<Neighbour> nearestNeighbours(double[] features) {
        return bruteForceNearest(features, k, trainingData);
    }

    /** Same query answered via the k-d tree, in expected O(log n) instead of O(n). */
    public List<Neighbour> kdNearestNeighbours(double[] features) {
        List<Neighbour> out = new ArrayList<>();
        for (KdTree.Neighbour n : kdTree.kNearest(features, k)) {
            out.add(new Neighbour(n.label(), n.distance()));
        }
        return out;
    }

    static List<Neighbour> bruteForceNearest(double[] features, int k, List<Example> data) {
        List<Example> sorted = new ArrayList<>(data);
        // Same total order as KdTree.kNearest: distance, then a deterministic
        // content-based secondary key, so both strategies agree even on exact ties.
        sorted.sort(Comparator
                .<Example>comparingDouble(e -> euclidean(features, e.features()))
                .thenComparing(e -> KdTree.secondaryKey(e.features())));
        List<Neighbour> out = new ArrayList<>();
        for (int i = 0; i < Math.min(k, sorted.size()); i++) {
            Example e = sorted.get(i);
            out.add(new Neighbour(e.label(), euclidean(features, e.features())));
        }
        return out;
    }

    /**
     * Majority vote over the k nearest neighbours.
     * Returns label -> vote count, ordered by votes descending.
     */
    public Map<String, Integer> predictVotes(double[] features) {
        return votesFrom(nearestNeighbours(features));
    }

    static Map<String, Integer> votesFrom(List<Neighbour> neighbours) {
        Map<String, Integer> votes = new LinkedHashMap<>();
        for (Neighbour n : neighbours) votes.merge(n.label(), 1, Integer::sum);
        Map<String, Integer> sorted = new LinkedHashMap<>();
        votes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    public String predict(double[] features) {
        Map<String, Integer> votes = predictVotes(features);
        return votes.isEmpty() ? null : votes.keySet().iterator().next();
    }

    /**
     * Leave-one-out cross-validation accuracy: for each training example,
     * run the brute-force vote using every other example and check whether
     * the held-out point's true label is predicted correctly. Not part of
     * the shipped prediction path — exists to give an apples-to-apples
     * accuracy comparison against {@link NaiveBayesClassifier#leaveOneOutAccuracy()}
     * on the ML Benchmark page.
     */
    public double leaveOneOutAccuracy() {
        if (trainingData.size() < 2) return Double.NaN;
        int correct = 0;
        for (int i = 0; i < trainingData.size(); i++) {
            List<Example> rest = new ArrayList<>(trainingData);
            Example held = rest.remove(i);
            Map<String, Integer> votes = votesFrom(bruteForceNearest(held.features(), k, rest));
            String predicted = votes.isEmpty() ? null : votes.keySet().iterator().next();
            if (held.label().equals(predicted)) correct++;
        }
        return correct / (double) trainingData.size();
    }

    private static double euclidean(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }
}
