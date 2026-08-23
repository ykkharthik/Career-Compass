package exception;

/** Thrown when a student profile fails validation (bad CGPA, empty skills, etc.). */
public class InvalidProfileException extends Exception {
    public InvalidProfileException(String message) {
        super(message);
    }
}
