package model;

import java.util.Set;

public class CloudEngineering extends CareerPath {
    @Override public String getName() { return "Cloud & DevOps Engineering"; }
    @Override public String getDescription() {
        return "Building and operating scalable infrastructure on cloud platforms.";
    }
    @Override public Set<String> getRequiredSkills() {
        return skills("aws", "linux", "docker", "kubernetes", "ci/cd", "networking", "scripting");
    }
    @Override public String[] getTypicalRoles() {
        return new String[]{"Cloud Engineer", "DevOps Engineer", "Site Reliability Engineer", "Platform Engineer"};
    }
}
