package service;

import model.CareerPath;
import model.Student;

import java.util.List;

/**
 * Benchmarks one student's fit for a domain against every other student
 * profile CareerCompass has on file, so a recommendation reads as "you're in
 * the top 15% for Data Science" rather than a bare, context-free score.
 */
public class PercentileService {

    public record Result(boolean available, int percentile, int peerCount) {}

    private static final int MIN_PEERS = 3;

    public Result percentileFor(Student target, CareerPath domain,
                                List<Student> allStudents, RecommendationService recommender) {
        double targetScore = scoreFor(target, domain, recommender);

        int peerCount = 0;
        int below = 0;
        for (Student other : allStudents) {
            if (other.getEmail().equalsIgnoreCase(target.getEmail())) continue;
            double otherScore = scoreFor(other, domain, recommender);
            peerCount++;
            if (otherScore < targetScore) below++;
        }

        if (peerCount < MIN_PEERS) return new Result(false, 0, peerCount);
        int percentile = (int) Math.round(100.0 * below / peerCount);
        return new Result(true, percentile, peerCount);
    }

    private double scoreFor(Student s, CareerPath domain, RecommendationService recommender) {
        for (var rec : recommender.recommend(s)) {
            if (rec.career.getName().equals(domain.getName())) return rec.finalScore;
        }
        return 0;
    }
}
