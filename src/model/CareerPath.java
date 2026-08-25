package model;

import java.util.Set;
import java.util.TreeSet;

/**
 * Abstract base for all career domains. Demonstrates inheritance +
 * polymorphism: each subclass supplies its own required-skill set and roles.
 */
public abstract class CareerPath {

    public abstract String getName();
    public abstract String getDescription();
    public abstract Set<String> getRequiredSkills();
    public abstract String[] getTypicalRoles();

    /** A TreeSet, not a hash-based Set: required skills always list alphabetically wherever they're shown. */
    protected static Set<String> skills(String... items) {
        Set<String> s = new TreeSet<>();
        for (String i : items) s.add(i.toLowerCase());
        return s;
    }

    // final: every subclass gets its display name through getName() (the
    // polymorphic hook), never by overriding Object's toString() again.
    @Override
    public final String toString() {
        return getName();
    }
}
