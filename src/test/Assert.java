package test;

import java.util.Objects;

/**
 * A minimal assertion helper — no JUnit, consistent with the rest of the
 * project. A failed assertion just throws {@link AssertionError}; TestRunner
 * catches it and reports the test as failed.
 */
public final class Assert {

    private Assert() {}

    public static void isTrue(String label, boolean condition) {
        if (!condition) throw new AssertionError(label + ": expected true");
    }

    public static void isFalse(String label, boolean condition) {
        if (condition) throw new AssertionError(label + ": expected false");
    }

    public static void equal(String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual))
            throw new AssertionError(label + ": expected <" + expected + "> but was <" + actual + ">");
    }

    public static void inRange(String label, double value, double min, double max) {
        if (value < min || value > max)
            throw new AssertionError(label + ": expected " + value + " to be within [" + min + ", " + max + "]");
    }
}
