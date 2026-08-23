package repository;

import model.Mentor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MentorRepository {
    private final String dataPath;
    private final List<Mentor> mentors = new ArrayList<>();

    public MentorRepository(String dataPath) {
        this.dataPath = dataPath;
        for (String line : FileManager.readLines(dataPath)) {
            try { mentors.add(Mentor.fromCsv(line)); } catch (RuntimeException ignored) { }
        }
    }

    public Optional<Mentor> findByEmail(String email) {
        return mentors.stream().filter(m -> m.email().equalsIgnoreCase(email)).findFirst();
    }

    public List<Mentor> findAll() { return new ArrayList<>(mentors); }

    public List<Mentor> findByDomain(String domain) {
        return mentors.stream().filter(m -> m.domain().equalsIgnoreCase(domain)).toList();
    }

    public void save(Mentor mentor) {
        mentors.removeIf(m -> m.email().equalsIgnoreCase(mentor.email()));
        mentors.add(mentor);
        persist();
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (Mentor m : mentors) lines.add(m.toCsv());
        FileManager.writeLines(dataPath, lines);
    }
}
