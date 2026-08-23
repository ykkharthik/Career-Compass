package repository;

import model.Faculty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FacultyRepository {
    private final String dataPath;
    private final List<Faculty> faculty = new ArrayList<>();

    public FacultyRepository(String dataPath) {
        this.dataPath = dataPath;
        for (String line : FileManager.readLines(dataPath)) {
            try { faculty.add(Faculty.fromCsv(line)); } catch (RuntimeException ignored) { }
        }
    }

    public Optional<Faculty> findByEmail(String email) {
        return faculty.stream().filter(f -> f.email().equalsIgnoreCase(email)).findFirst();
    }

    public void save(Faculty f) {
        faculty.removeIf(x -> x.email().equalsIgnoreCase(f.email()));
        faculty.add(f);
        List<String> lines = new ArrayList<>();
        for (Faculty x : faculty) lines.add(x.toCsv());
        FileManager.writeLines(dataPath, lines);
    }
}
