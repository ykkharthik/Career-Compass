package test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A from-scratch test runner — no JUnit, no external libraries, matching the
 * rest of the project. Uses reflection to discover every public no-arg
 * method starting with "test" on each registered class, invokes it, and
 * reports pass/fail. Run with:
 *
 * <pre>  java -cp out test.TestRunner</pre>
 *
 * Exits with status 1 if any test fails, so it can gate a build the same
 * way a real test runner would.
 */
public final class TestRunner {

    public static void main(String[] args) {
        Class<?>[] suites = {
                KnnCareerClassifierTest.class,
                NaiveBayesClassifierTest.class,
                KdTreeCorrectnessTest.class,
                RecommendationServiceTest.class,
                SkillGapServiceTest.class,
                NotificationRepositoryTest.class,
                AuthServiceTest.class,
        };

        int passed = 0, failed = 0;
        List<String> failures = new ArrayList<>();

        for (Class<?> suite : suites) {
            Object instance;
            try {
                instance = suite.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.out.println("  ERROR  could not set up " + suite.getSimpleName() + ": " + e);
                failed++;
                continue;
            }

            for (Method m : suite.getMethods()) {
                if (!m.getName().startsWith("test") || m.getParameterCount() != 0) continue;
                String label = suite.getSimpleName() + "." + m.getName();
                try {
                    m.invoke(instance);
                    System.out.println("  PASS  " + label);
                    passed++;
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    System.out.println("  FAIL  " + label + " — " + cause.getMessage());
                    failures.add(label);
                    failed++;
                }
            }
        }

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (!failures.isEmpty()) {
            System.out.println("Failed: " + failures);
            System.exit(1);
        }
    }
}
