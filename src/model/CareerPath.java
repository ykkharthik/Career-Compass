package model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Abstract base for all career domains. Demonstrates inheritance +
 * polymorphism: each subclass supplies its own required-skill set and roles.
 */
public abstract class CareerPath {

    public abstract String getName();
    public abstract String getDescription();
    public abstract Set<String> getRequiredSkills();
    public abstract String[] getTypicalRoles();

    protected static Set<String> skills(String... items) {
        Set<String> s = new LinkedHashSet<>();
        for (String i : items) s.add(i.toLowerCase());
        return s;
    }

    @Override
    public String toString() {
        return getName();
    }
}
