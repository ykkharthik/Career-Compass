package test;

import exception.InvalidProfileException;
import ml.KnnCareerClassifier;
import ml.NaiveBayesClassifier;
import model.CareerPath;
import model.CloudEngineering;
import model.Cybersecurity;
import model.DataScience;
import model.ProductManagement;
import model.SoftwareEngineering;
import model.Student;
import model.UiUxDesign;
import service.RecommendationService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RecommendationServiceTest {

    private final List<CareerPath> careers = List.of(
            new SoftwareEngineering(), new DataScience(), new Cybersecurity(),
            new CloudEngineering(), new UiUxDesign(), new ProductManagement());
    private final KnnCareerClassifier classifier = new KnnCareerClassifier("data/career_training.csv", 5);
    private final NaiveBayesClassifier naiveBayes = new NaiveBayesClassifier("data/career_training.csv");
    private final RecommendationService recommender = new RecommendationService(careers, classifier, naiveBayes);

    public void testFinalScoreIsWeightedBlend() throws InvalidProfileException {
        Student student = studentWithAllSoftwareEngineeringSkills();
        for (var rec : recommender.recommend(student)) {
            double expected = 0.5 * rec.ruleScore + 0.25 * rec.knnScore + 0.25 * rec.nbScore;
            Assert.inRange(rec.career.getName() + ": finalScore matches the documented 50/25/25 blend",
                    Math.abs(rec.finalScore - expected), 0, 1e-9);
        }
    }

    public void testStudentWithAllRequiredSkillsRanksThatDomainHighly() throws InvalidProfileException {
        Student student = studentWithAllSoftwareEngineeringSkills();
        var recs = recommender.recommend(student);
        var softwareEngineering = recs.stream()
                .filter(r -> r.career.getName().equals("Software Engineering")).findFirst().orElseThrow();
        // Having every required skill should push the rule component near its ceiling.
        Assert.inRange("rule score for a fully-matching profile", softwareEngineering.ruleScore, 0.8, 1.0);
    }

    public void testEndorsedSkillsCountForMoreThanSelfReported() throws InvalidProfileException {
        Student student = studentWithAllSoftwareEngineeringSkills();
        var unendorsed = recommender.recommend(student, Set.of());
        var endorsed = recommender.recommend(student, student.getSkills());
        double unendorsedRule = ruleScoreFor(unendorsed, "Software Engineering");
        double endorsedRule = ruleScoreFor(endorsed, "Software Engineering");
        Assert.isTrue("endorsing every skill should not lower the rule score",
                endorsedRule >= unendorsedRule);
    }

    private double ruleScoreFor(List<RecommendationService.Recommendation> recs, String domain) {
        return recs.stream().filter(r -> r.career.getName().equals(domain)).findFirst().orElseThrow().ruleScore;
    }

    private Student studentWithAllSoftwareEngineeringSkills() throws InvalidProfileException {
        Set<String> skills = new LinkedHashSet<>(new SoftwareEngineering().getRequiredSkills());
        return new Student("test@example.com", "Test Student", 8.5, skills, 5, 3, 2, 2, 2);
    }
}
