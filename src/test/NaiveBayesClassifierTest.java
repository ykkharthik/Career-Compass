package test;

import ml.NaiveBayesClassifier;

public class NaiveBayesClassifierTest {

    private final NaiveBayesClassifier classifier = new NaiveBayesClassifier("data/career_training.csv");

    public void testTrainingDataLoads() {
        Assert.isTrue("classifier should be ready", classifier.isReady());
        Assert.equal("training set size", 43, classifier.trainingSize());
    }

    public void testPredictsKnownProfile() {
        double[] softwareEngineeringProfile = {5, 3, 2, 2, 2, 4.2};
        Assert.equal("prediction for a known SE profile", "Software Engineering",
                classifier.predict(softwareEngineeringProfile));
    }

    public void testProbabilitiesSumToOne() {
        double[] profile = {3, 3, 3, 3, 3, 2.5};
        var probs = classifier.predictProbabilities(profile);
        double sum = probs.values().stream().mapToDouble(Double::doubleValue).sum();
        Assert.inRange("posterior probabilities should sum to ~1", sum, 0.999, 1.001);
    }

    /** Leave-one-out accuracy should be well above chance (1/6 domains) — a sanity bound, not an exact figure. */
    public void testLeaveOneOutAccuracyIsReasonable() {
        double accuracy = classifier.leaveOneOutAccuracy();
        Assert.inRange("Naive Bayes leave-one-out accuracy", accuracy, 0.4, 1.0);
    }
}
