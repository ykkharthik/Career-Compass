package repository;

import model.Mentor;

import java.util.ArrayList;
import java.util.Iterator;
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
        // An explicit Iterator, not removeIf/for-each: this loop removes
        // while traversing, which for-each can't do safely (it would throw
        // ConcurrentModificationException) - Iterator.remove() is the
        // collections-framework-sanctioned way to do it.
        Iterator<Mentor> it = mentors.iterator();
        while (it.hasNext()) {
            if (it.next().email().equalsIgnoreCase(mentor.email())) it.remove();
        }
        mentors.add(mentor);
        persist();
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (Mentor m : mentors) lines.add(m.toCsv());
        FileManager.writeLines(dataPath, lines);
    }
}
