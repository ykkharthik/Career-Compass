package test;

import ml.KnnCareerClassifier;

import java.util.List;
import java.util.Random;

/**
 * Regression guard for a real bug caught during development: KdTree.kNearest's
 * final sort once ordered by distance only, dropping the secondary tie-break
 * key, so two neighbours tied at the k-th boundary could come out in a
 * different order than brute force (see README and the git history for the
 * fix). This test would have failed before that fix and must keep passing.
 */
public class KdTreeCorrectnessTest {

    public void testAgreesWithBruteForceOnAllTrainingPoints() {
        for (int k : new int[]{1, 3, 5, 10}) {
            KnnCareerClassifier classifier = new KnnCareerClassifier("data/career_training.csv", k);
            for (var example : classifier.examples()) {
                assertSameNeighbours("k=" + k + ", training point as query",
                        classifier.nearestNeighbours(example.features()),
                        classifier.kdNearestNeighbours(example.features()));
            }
        }
    }

    public void testAgreesWithBruteForceOnRandomQueries() {
        Random random = new Random(7); // fixed seed: deterministic, reproducible failures
        KnnCareerClassifier classifier = new KnnCareerClassifier("data/career_training.csv", 5);
        for (int i = 0; i < 2000; i++) {
            double[] query = {
                    1 + random.nextDouble() * 4, 1 + random.nextDouble() * 4, 1 + random.nextDouble() * 4,
                    1 + random.nextDouble() * 4, 1 + random.nextDouble() * 4, random.nextDouble() * 5};
            assertSameNeighbours("random query #" + i,
                    classifier.nearestNeighbours(query), classifier.kdNearestNeighbours(query));
        }
    }

    private static void assertSameNeighbours(String label, List<KnnCareerClassifier.Neighbour> bruteForce,
            List<KnnCareerClassifier.Neighbour> kdTree) {
        Assert.equal(label + ": neighbour count", bruteForce.size(), kdTree.size());
        for (int i = 0; i < bruteForce.size(); i++) {
            Assert.equal(label + ": neighbour[" + i + "] label",
                    bruteForce.get(i).label(), kdTree.get(i).label());
        }
    }
}
