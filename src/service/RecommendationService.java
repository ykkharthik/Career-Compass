package service;

import ml.KnnCareerClassifier;
import ml.NaiveBayesClassifier;
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
 *  2. k-NN score        — majority vote from labelled example profiles
 *     (instance-based learning: compares a profile to its closest examples).
 *  3. Naive Bayes score — posterior probability from a Gaussian fitted per
 *     feature per domain (generative learning: asks which domain's fitted
 *     distributions best explain this profile).
 * Rules get 50% of the final score and the two learning paradigms split the
 * other 50% evenly — two different models agreeing is stronger evidence
 * than either alone. Every component is exposed so recommendations remain
 * explainable; see the ML Benchmark page for how the two learners compare
 * on accuracy (leave-one-out cross-validation) and, for k-NN specifically,
 * lookup strategy (brute force vs k-d tree).
 */
public class RecommendationService {

    private static final double ENDORSED_SKILL_WEIGHT = 1.3; // vs 1.0 for a self-reported skill

    /** One ranked recommendation with a score breakdown for explainability. */
    public static class Recommendation {
        public final CareerPath career;
        public final double ruleScore;      // 0..1
        public final double knnScore;       // 0..1
        public final double nbScore;        // 0..1
        public final double finalScore;     // 0..1
        public final List<String> reasons = new ArrayList<>();

        Recommendation(CareerPath career, double ruleScore, double knnScore, double nbScore) {
            this.career = career;
            this.ruleScore = ruleScore;
            this.knnScore = knnScore;
            this.nbScore = nbScore;
            this.finalScore = 0.5 * ruleScore + 0.25 * knnScore + 0.25 * nbScore;
        }
    }

    private final List<CareerPath> careers;
    private final KnnCareerClassifier classifier;
    private final NaiveBayesClassifier naiveBayes;

    public RecommendationService(List<CareerPath> careers, KnnCareerClassifier classifier,
            NaiveBayesClassifier naiveBayes) {
        this.careers = careers;
        this.classifier = classifier;
        this.naiveBayes = naiveBayes;
    }

    public List<Recommendation> recommend(Student student) {
        return recommend(student, Set.of());
    }

    public List<Recommendation> recommend(Student student, Set<String> endorsedSkills) {
        double[] features = student.toFeatureVector();
        Map<String, Integer> knnVotes = classifier.isReady()
                ? classifier.predictVotes(features)
                : Map.of();
        int k = Math.max(1, knnVotes.values().stream().mapToInt(Integer::intValue).sum());
        Map<String, Double> nbProbs = naiveBayes.isReady()
                ? naiveBayes.predictProbabilities(features)
                : Map.of();

        List<Recommendation> results = new ArrayList<>();
        for (CareerPath career : careers) {
            double ruleScore = ruleScore(student, career, endorsedSkills);
            double knnScore = knnVotes.getOrDefault(career.getName(), 0) / (double) k;
            double nbScore = nbProbs.getOrDefault(career.getName(), 0.0);

            Recommendation rec = new Recommendation(career, ruleScore, knnScore, nbScore);
            explain(rec, student, career, knnVotes, nbProbs, endorsedSkills);
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

    private void explain(Recommendation rec, Student s, CareerPath career, Map<String, Integer> knnVotes,
                         Map<String, Double> nbProbs, Set<String> endorsedSkills) {
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
        Double nbProb = nbProbs.get(career.getName());
        // Baseline (no signal either way) is 1/domain-count; only call this
        // out when the probabilistic model is meaningfully above that.
        double baseline = careers.isEmpty() ? 0 : 1.0 / careers.size();
        if (nbProb != null && nbProb > baseline * 1.3) {
            rec.reasons.add(String.format("The probabilistic model estimates a %.0f%% likelihood for this "
                    + "domain from your interest/CGPA profile (Naive Bayes).", nbProb * 100));
        }
    }
}
