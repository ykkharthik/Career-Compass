package test;

import ml.KnnCareerClassifier;

public class KnnCareerClassifierTest {

    private final KnnCareerClassifier classifier = new KnnCareerClassifier("data/career_training.csv", 5);

    public void testTrainingDataLoads() {
        Assert.isTrue("classifier should be ready with 43 training rows", classifier.isReady());
        Assert.equal("training set size", 43, classifier.trainingSize());
    }

    /** A profile identical to a known Software Engineering training row should predict that domain. */
    public void testPredictsKnownProfile() {
        double[] softwareEngineeringProfile = {5, 3, 2, 2, 2, 4.2};
        Assert.equal("prediction for a known SE profile", "Software Engineering",
                classifier.predict(softwareEngineeringProfile));
    }

    public void testVotesSumToK() {
        double[] profile = {3, 3, 3, 3, 3, 2.5};
        var votes = classifier.predictVotes(profile);
        int total = votes.values().stream().mapToInt(Integer::intValue).sum();
        Assert.equal("total votes should equal k", classifier.k(), total);
    }

    /** Leave-one-out accuracy should be well above chance (1/6 domains) — a sanity bound, not an exact figure. */
    public void testLeaveOneOutAccuracyIsReasonable() {
        double accuracy = classifier.leaveOneOutAccuracy();
        Assert.inRange("k-NN leave-one-out accuracy", accuracy, 0.5, 1.0);
    }
}
