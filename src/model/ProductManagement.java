package model;

import java.util.Set;

public class ProductManagement extends CareerPath {
    @Override public String getName() { return "Product Management"; }
    @Override public String getDescription() {
        return "Owning product strategy and coordinating engineering, design and business.";
    }
    @Override public Set<String> getRequiredSkills() {
        return skills("communication", "market research", "agile", "data analysis", "roadmapping", "stakeholder management", "sql");
    }
    @Override public String[] getTypicalRoles() {
        return new String[]{"Associate Product Manager", "Product Analyst", "Program Manager", "Product Owner"};
    }
}
