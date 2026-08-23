package model;

import java.util.Set;

public class UiUxDesign extends CareerPath {
    @Override public String getName() { return "UI/UX Design"; }
    @Override public String getDescription() {
        return "Designing intuitive, human-centred digital product experiences.";
    }
    @Override public Set<String> getRequiredSkills() {
        return skills("figma", "wireframing", "user research", "prototyping", "visual design", "html", "css");
    }
    @Override public String[] getTypicalRoles() {
        return new String[]{"UI Designer", "UX Designer", "Product Designer", "Interaction Designer"};
    }
}
