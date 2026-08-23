package repository;

import exception.InvalidProfileException;
import model.Student;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** CRUD over student profiles with CSV persistence. One profile per login email. */
public class StudentRepository {

    private final String dataPath;
    private final List<Student> students = new ArrayList<>();

    public StudentRepository(String dataPath) {
        this.dataPath = dataPath;
        for (String line : FileManager.readLines(dataPath)) {
            try {
                students.add(Student.fromCsv(line));
            } catch (InvalidProfileException | RuntimeException e) {
                System.out.println("[warn] Skipping bad row in " + dataPath + ": " + e.getMessage());
            }
        }
    }

    public Optional<Student> findByEmail(String email) {
        return students.stream()
                .filter(s -> s.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    public List<Student> findAll() {
        return new ArrayList<>(students);
    }

    /** Create-or-update: profiles are keyed by email. */
    public void save(Student student) {
        students.removeIf(s -> s.getEmail().equalsIgnoreCase(student.getEmail()));
        students.add(student);
        persist();
    }

    public boolean delete(String email) {
        boolean removed = students.removeIf(s -> s.getEmail().equalsIgnoreCase(email));
        if (removed) persist();
        return removed;
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (Student s : students) lines.add(s.toCsv());
        FileManager.writeLines(dataPath, lines);
    }
}
