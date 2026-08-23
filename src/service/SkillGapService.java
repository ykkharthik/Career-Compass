package service;

import model.CareerPath;
import model.Student;

import java.util.LinkedHashSet;
import java.util.Set;

/** Set-difference skill gap analysis + a simple ordered development plan. */
public class SkillGapService {

    public Set<String> missingSkills(Student student, CareerPath career) {
        Set<String> missing = new LinkedHashSet<>(career.getRequiredSkills());
        missing.removeAll(student.getSkills());
        return missing;
    }

    public Set<String> matchedSkills(Student student, CareerPath career) {
        Set<String> matched = new LinkedHashSet<>(student.getSkills());
        matched.retainAll(career.getRequiredSkills());
        return matched;
    }

    public void printPlan(Student student, CareerPath career) {
        Set<String> matched = matchedSkills(student, career);
        Set<String> missing = missingSkills(student, career);

        System.out.println("\n--- Skill Gap Analysis: " + career.getName() + " ---");
        System.out.println("Already have (" + matched.size() + "): "
                + (matched.isEmpty() ? "(none)" : String.join(", ", matched)));
        System.out.println("Still need  (" + missing.size() + "): "
                + (missing.isEmpty() ? "(none - fully ready!)" : String.join(", ", missing)));

        if (!missing.isEmpty()) {
            System.out.println("\nSuggested development plan:");
            int month = 1;
            for (String skill : missing) {
                System.out.printf("  Month %d: learn '%s' (course + one mini project)%n", month++, skill);
            }
        }
    }
}
