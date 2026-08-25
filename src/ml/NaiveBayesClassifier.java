package ml;

import repository.FileManager;
import util.Rankings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gaussian Naive Bayes, implemented from scratch (no external ML libraries)
 * as a second, genuinely different learning paradigm alongside
 * {@link KnnCareerClassifier}. Where k-NN is instance-based — it compares a
 * new profile directly against its closest labelled examples — this is
 * generative: it fits a Gaussian distribution per feature per career domain
 * from the training data, then asks which domain's fitted distributions
 * best explain the new profile (Bayes' rule, assuming the features are
 * independent given the class — the "naive" part).
 *
 * Same training data and feature vector as KnnCareerClassifier
 * (data/career_training.csv: 5 interest ratings + scaled CGPA -> domain),
 * so the two can be compared on equal footing — see leaveOneOutAccuracy()
 * and the ML Benchmark page.
 */
public class NaiveBayesClassifier {

    private record ClassStats(String label, double prior, double[] mean, double[] variance) {}

    // A feature that happens to be constant within one class's training rows
    // would otherwise produce zero variance -> a division by zero -> an
    // infinitely confident (and likely wrong) Gaussian. Flooring variance
    // caps how confident any single feature is allowed to make the model.
    private static final double MIN_VARIANCE = 1e-2;

    private final List<KnnCareerClassifier.Example> trainingData = new ArrayList<>();
    private final List<ClassStats> classStats;

    public NaiveBayesClassifier(String trainingCsvPath) {
        for (String line : FileManager.readLines(trainingCsvPath)) {
            String[] p = line.split(",", -1);
            if (p.length < 7) continue;
            try {
                double[] f = new double[6];
                for (int i = 0; i < 6; i++) f[i] = Double.parseDouble(p[i]);
                trainingData.add(new KnnCareerClassifier.Example(f, p[6].trim()));
            } catch (NumberFormatException ignored) {
                // header row or bad line
            }
        }
        this.classStats = fit(trainingData);
    }

    private static List<ClassStats> fit(List<KnnCareerClassifier.Example> data) {
        Map<String, List<double[]>> byLabel = new LinkedHashMap<>();
        for (var e : data) byLabel.computeIfAbsent(e.label(), key -> new ArrayList<>()).add(e.features());

        List<ClassStats> stats = new ArrayList<>();
        int total = data.size();
        int dims = data.isEmpty() ? 0 : data.get(0).features().length;
        for (var entry : byLabel.entrySet()) {
            List<double[]> rows = entry.getValue();
            double[] mean = new double[dims];
            double[] variance = new double[dims];
            for (double[] row : rows)
                for (int d = 0; d < dims; d++) mean[d] += row[d];
            for (int d = 0; d < dims; d++) mean[d] /= rows.size();
            for (double[] row : rows)
                for (int d = 0; d < dims; d++) variance[d] += (row[d] - mean[d]) * (row[d] - mean[d]);
            for (int d = 0; d < dims; d++) variance[d] = Math.max(MIN_VARIANCE, variance[d] / rows.size());
            stats.add(new ClassStats(entry.getKey(), rows.size() / (double) total, mean, variance));
        }
        return stats;
    }

    public boolean isReady() { return !classStats.isEmpty(); }

    public int trainingSize() { return trainingData.size(); }

    /** Posterior probability per career domain, summing to 1 across all domains. */
    public Map<String, Double> predictProbabilities(double[] features) {
        return posteriors(features, classStats);
    }

    public String predict(double[] features) {
        return Rankings.argMax(predictProbabilities(features));
    }

    private static Map<String, Double> posteriors(double[] features, List<ClassStats> stats) {
        Map<String, Double> logPosterior = new LinkedHashMap<>();
        for (ClassStats cs : stats) {
            double logP = Math.log(cs.prior());
            for (int d = 0; d < features.length; d++)
                logP += logGaussian(features[d], cs.mean()[d], cs.variance()[d]);
            logPosterior.put(cs.label(), logP);
        }
        // Softmax over the log-posteriors, shifted by the max for numerical
        // stability, so callers get plain 0..1 probabilities that sum to 1
        // instead of raw (and much harder to compare) log-likelihoods.
        double max = logPosterior.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double sum = 0;
        Map<String, Double> exp = new LinkedHashMap<>();
        for (var e : logPosterior.entrySet()) {
            double v = Math.exp(e.getValue() - max);
            exp.put(e.getKey(), v);
            sum += v;
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (var e : exp.entrySet()) result.put(e.getKey(), sum == 0 ? 0 : e.getValue() / sum);
        return result;
    }

    private static double logGaussian(double x, double mean, double variance) {
        return -0.5 * Math.log(2 * Math.PI * variance) - ((x - mean) * (x - mean)) / (2 * variance);
    }

    /**
     * Leave-one-out cross-validation accuracy: for each training example,
     * refit on every other example and check whether the held-out point's
     * true label is predicted correctly. Not part of the shipped prediction
     * path — this exists to give an apples-to-apples accuracy comparison
     * against {@link KnnCareerClassifier#leaveOneOutAccuracy()} on the ML
     * Benchmark page, since a raw training-set fit would flatter this model
     * (it would already have "seen" the point it's asked to classify).
     */
    public double leaveOneOutAccuracy() {
        if (trainingData.size() < 2) return Double.NaN;
        int correct = 0;
        for (int i = 0; i < trainingData.size(); i++) {
            List<KnnCareerClassifier.Example> rest = new ArrayList<>(trainingData);
            KnnCareerClassifier.Example held = rest.remove(i);
            String predicted = Rankings.argMax(posteriors(held.features(), fit(rest)));
            if (held.label().equals(predicted)) correct++;
        }
        return correct / (double) trainingData.size();
    }
}
