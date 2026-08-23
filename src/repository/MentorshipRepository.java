package repository;

import model.MentorshipRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MentorshipRepository {
    private final String dataPath;
    private final List<MentorshipRequest> requests = new ArrayList<>();

    public MentorshipRepository(String dataPath) {
        this.dataPath = dataPath;
        for (String line : FileManager.readLines(dataPath)) {
            try { requests.add(MentorshipRequest.fromCsv(line)); } catch (RuntimeException ignored) { }
        }
    }

    public List<MentorshipRequest> findByStudent(String email) {
        return requests.stream().filter(r -> r.studentEmail().equalsIgnoreCase(email)).toList();
    }

    public List<MentorshipRequest> findByMentor(String email) {
        return requests.stream().filter(r -> r.mentorEmail().equalsIgnoreCase(email)).toList();
    }

    public Optional<MentorshipRequest> findById(String id) {
        return requests.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public void save(MentorshipRequest request) {
        requests.removeIf(r -> r.id().equals(request.id()));
        requests.add(request);
        persist();
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (MentorshipRequest r : requests) lines.add(r.toCsv());
        FileManager.writeLines(dataPath, lines);
    }
}
