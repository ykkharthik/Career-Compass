package model;

import java.util.Set;

public class Cybersecurity extends CareerPath {
    @Override public String getName() { return "Cybersecurity"; }
    @Override public String getDescription() {
        return "Protecting systems, networks and data from digital attacks.";
    }
    @Override public Set<String> getRequiredSkills() {
        return skills("networking", "linux", "cryptography", "ethical hacking", "python", "security fundamentals", "risk assessment");
    }
    @Override public String[] getTypicalRoles() {
        return new String[]{"Security Analyst", "SOC Analyst", "Penetration Tester", "Security Engineer"};
    }
}
