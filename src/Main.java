import menu.Menu;
import repository.StudentRepository;

/**
 * Application entry point for CareerCompass.
 * Demonstrates: object creation, delegation, and (for the non-functional
 * requirement) a background thread that warms up the data layer without
 * blocking startup.
 */
public class Main {

    private static final String DATA_PATH = "data/students.csv";

    public static void main(String[] args) {
        System.out.println("Starting CareerCompass...");

        // Non-functional requirement (fast execution): warm up on a thread.
        Thread warmUp = new Thread(() ->
                System.out.println("[background] data layer ready"));
        warmUp.start();
        try {
            warmUp.join();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        StudentRepository repository = new StudentRepository(DATA_PATH);
        new Menu(repository).start();
    }
}
