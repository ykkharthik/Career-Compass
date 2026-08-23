package service;

import ml.KnnCareerClassifier;
import model.CareerPath;
import model.Student;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid recommendation engine:
 *  1. Rule-based score  — skill overlap with each career's required skills
 *     (with faculty-endorsed skills weighted higher, since a verified skill
 *     is worth more evidence than a self-reported one), plus interest
 *     alignment (transparent, hand-written rules).
 *  2. k-NN score        — majority vote from labelled example profiles.
 * The two are combined 50/50 into a final ranking, and every component of the
 * score is exposed so recommendations remain explainable.
 */
public class RecommendationService {

    private static final double ENDORSED_SKILL_WEIGHT = 1.3; // vs 1.0 for a self-reported skill

    /** One ranked recommendation with a score breakdown for explainability. */
    public static class Recommendation {
        public final CareerPath career;
        public final double ruleScore;      // 0..1
        public final double knnScore;       // 0..1
        public final double finalScore;     // 0..1
        public final List<String> reasons = new ArrayList<>();

        Recommendation(CareerPath career, double ruleScore, double knnScore) {
            this.career = career;
            this.ruleScore = ruleScore;
            this.knnScore = knnScore;
            this.finalScore = 0.5 * ruleScore + 0.5 * knnScore;
        }
    }

    private final List<CareerPath> careers;
    private final KnnCareerClassifier classifier;

    public RecommendationService(List<CareerPath> careers, KnnCareerClassifier classifier) {
        this.careers = careers;
        this.classifier = classifier;
    }

    public List<Recommendation> recommend(Student student) {
        return recommend(student, Set.of());
    }

    public List<Recommendation> recommend(Student student, Set<String> endorsedSkills) {
        Map<String, Integer> knnVotes = classifier.isReady()
                ? classifier.predictVotes(student.toFeatureVector())
                : Map.of();
        int k = Math.max(1, knnVotes.values().stream().mapToInt(Integer::intValue).sum());

        List<Recommendation> results = new ArrayList<>();
        for (CareerPath career : careers) {
            double ruleScore = ruleScore(student, career, endorsedSkills);
            double knnScore = knnVotes.getOrDefault(career.getName(), 0) / (double) k;

            Recommendation rec = new Recommendation(career, ruleScore, knnScore);
            explain(rec, student, career, knnVotes, endorsedSkills);
            results.add(rec);
        }
        results.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
        return results;
    }

    /** Transparent hand-written rules: 60% skill overlap + 40% interest alignment. */
    private double ruleScore(Student s, CareerPath career, Set<String> endorsedSkills) {
        Set<String> required = career.getRequiredSkills();
        Set<String> overlap = new HashSet<>(s.getSkills());
        overlap.retainAll(required);

        double weightedOverlap = 0;
        for (String sk : overlap) weightedOverlap += endorsedSkills.contains(sk) ? ENDORSED_SKILL_WEIGHT : 1.0;
        double skillPart = required.isEmpty() ? 0 : Math.min(1.0, weightedOverlap / required.size());

        double interestPart = switch (career.getName()) {
            case "Software Engineering"        -> s.getInterestCoding() / 5.0;
            case "Data Science"                -> (s.getInterestMath() * 0.6 + s.getInterestCoding() * 0.4) / 5.0;
            case "Cybersecurity"               -> (s.getInterestSecurity() * 0.7 + s.getInterestCoding() * 0.3) / 5.0;
            case "Cloud & DevOps Engineering"  -> (s.getInterestCoding() * 0.6 + s.getInterestSecurity() * 0.4) / 5.0;
            case "UI/UX Design"                -> s.getInterestDesign() / 5.0;
            case "Product Management"          -> (s.getInterestCommunication() * 0.7 + s.getInterestDesign() * 0.3) / 5.0;
            default -> 0.5;
        };
        return 0.6 * skillPart + 0.4 * interestPart;
    }

    private void explain(Recommendation rec, Student s, CareerPath career,
                         Map<String, Integer> knnVotes, Set<String> endorsedSkills) {
        Set<String> overlap = new HashSet<>(s.getSkills());
        overlap.retainAll(career.getRequiredSkills());
        if (!overlap.isEmpty()) {
            rec.reasons.add("You already have " + overlap.size() + "/"
                    + career.getRequiredSkills().size() + " required skills: "
                    + String.join(", ", overlap));
        } else {
            rec.reasons.add("No required skills yet - this would be a fresh start.");
        }
        Set<String> endorsedOverlap = new HashSet<>(overlap);
        endorsedOverlap.retainAll(endorsedSkills);
        if (!endorsedOverlap.isEmpty()) {
            rec.reasons.add("Faculty-verified skills counted extra: " + String.join(", ", endorsedOverlap));
        }
        Integer votes = knnVotes.get(career.getName());
        if (votes != null && votes > 0) {
            rec.reasons.add(votes + " of your closest matching example profiles chose this path (k-NN).");
        }
    }
}
