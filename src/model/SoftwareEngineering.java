package model;

import java.util.Set;

public class SoftwareEngineering extends CareerPath {
    @Override public String getName() { return "Software Engineering"; }
    @Override public String getDescription() {
        return "Designing, building and maintaining software systems and applications.";
    }
    @Override public Set<String> getRequiredSkills() {
        return skills("java", "data structures", "algorithms", "git", "sql", "oop", "problem solving");
    }
    @Override public String[] getTypicalRoles() {
        return new String[]{"Backend Developer", "Full-Stack Developer", "SDE-1", "Mobile Developer"};
    }
}
