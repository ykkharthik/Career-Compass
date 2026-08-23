package service;

import model.Application;
import model.CareerPath;
import model.Student;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Platform-wide analytics computed from the same data the recommendation
 * engine already uses — not a separate reporting pipeline. Answers the
 * questions a recruiter, faculty member, or admin actually has about the
 * student population as a whole: which domains are students gravitating
 * toward, which skills are common vs. scarce, and where the internship
 * pipeline currently stands.
 */
public class TrendsService {

    /** How many students have each domain as their #1 recommendation. */
    public Map<String, Integer> domainFitCounts(List<Student> students, RecommendationService recommender,
                                                Map<String, Set<String>> endorsedSkillsByStudent) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Student s : students) {
            var endorsed = endorsedSkillsByStudent.getOrDefault(s.getEmail(), Set.of());
            var recs = recommender.recommend(s, endorsed);
            if (recs.isEmpty()) continue;
            String top = recs.get(0).career.getName();
            counts.merge(top, 1, Integer::sum);
        }
        return sortByValueDesc(counts);
    }

    /** Frequency of every skill across all student profiles, most common first. */
    public Map<String, Integer> skillFrequency(List<Student> students, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Student s : students)
            for (String skill : s.getSkills())
                counts.merge(skill, 1, Integer::sum);
        return top(sortByValueDesc(counts), limit);
    }

    /**
     * Frequency of skills missing from each student's #1-recommended domain,
     * aggregated across the whole population — the platform-wide "what's
     * everyone short on" signal that tells faculty what to teach next.
     */
    public Map<String, Integer> topSkillGaps(List<Student> students, RecommendationService recommender,
                                             SkillGapService skillGap,
                                             Map<String, Set<String>> endorsedSkillsByStudent, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Student s : students) {
            var endorsed = endorsedSkillsByStudent.getOrDefault(s.getEmail(), Set.of());
            var recs = recommender.recommend(s, endorsed);
            if (recs.isEmpty()) continue;
            CareerPath top = recs.get(0).career;
            for (String missing : skillGap.missingSkills(s, top))
                counts.merge(missing, 1, Integer::sum);
        }
        return top(sortByValueDesc(counts), limit);
    }

    /** Application counts per pipeline status, in a fixed funnel order. */
    public Map<String, Integer> applicationFunnel(List<Application> applications) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String status : new String[]{"APPLIED", "SHORTLISTED", "INTERVIEW", "OFFER", "REJECTED"})
            counts.put(status, 0);
        for (Application a : applications) counts.merge(a.status(), 1, Integer::sum);
        return counts;
    }

    /** Average CGPA of students whose #1 recommendation is each domain. */
    public Map<String, Double> averageCgpaByTopDomain(List<Student> students, RecommendationService recommender,
                                                       Map<String, Set<String>> endorsedSkillsByStudent) {
        Map<String, List<Double>> byDomain = new LinkedHashMap<>();
        for (Student s : students) {
            var endorsed = endorsedSkillsByStudent.getOrDefault(s.getEmail(), Set.of());
            var recs = recommender.recommend(s, endorsed);
            if (recs.isEmpty()) continue;
            byDomain.computeIfAbsent(recs.get(0).career.getName(), d -> new java.util.ArrayList<>())
                    .add(s.getCgpa());
        }
        Map<String, Double> averages = new LinkedHashMap<>();
        for (var entry : byDomain.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            averages.put(entry.getKey(), avg);
        }
        return averages;
    }

    private Map<String, Integer> sortByValueDesc(Map<String, Integer> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, Integer> top(Map<String, Integer> sorted, int limit) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int i = 0;
        for (var entry : sorted.entrySet()) {
            if (i++ >= limit) break;
            out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }
}
