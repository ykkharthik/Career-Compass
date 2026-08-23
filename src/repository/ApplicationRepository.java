package repository;

import model.Application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ApplicationRepository {
    private final String dataPath;
    private final List<Application> applications = new ArrayList<>();

    public ApplicationRepository(String dataPath) {
        this.dataPath = dataPath;
        for (String line : FileManager.readLines(dataPath)) {
            try { applications.add(Application.fromCsv(line)); } catch (RuntimeException ignored) { }
        }
    }

    public List<Application> findAll() {
        return applications.stream()
                .sorted(Comparator.comparing(Application::updatedAt).reversed())
                .toList();
    }

    public List<Application> findByStudent(String email) {
        return applications.stream()
                .filter(a -> a.studentEmail().equalsIgnoreCase(email))
                .sorted(Comparator.comparing(Application::appliedAt).reversed())
                .toList();
    }

    public Optional<Application> findById(String id) {
        return applications.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    public boolean alreadyApplied(String studentEmail, String internshipTitle) {
        return applications.stream().anyMatch(a ->
                a.studentEmail().equalsIgnoreCase(studentEmail)
                        && a.internshipTitle().equalsIgnoreCase(internshipTitle));
    }

    public void save(Application a) {
        applications.removeIf(x -> x.id().equals(a.id()));
        applications.add(a);
        persist();
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (Application a : applications) lines.add(a.toCsv());
        FileManager.writeLines(dataPath, lines);
    }
}
