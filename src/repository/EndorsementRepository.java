package repository;

import model.Endorsement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EndorsementRepository {
    private final String dataPath;
    private final List<Endorsement> endorsements = new ArrayList<>();

    public EndorsementRepository(String dataPath) {
        this.dataPath = dataPath;
        for (String line : FileManager.readLines(dataPath)) {
            try { endorsements.add(Endorsement.fromCsv(line)); } catch (RuntimeException ignored) { }
        }
    }

    public List<Endorsement> findByStudent(String email) {
        return endorsements.stream().filter(e -> e.studentEmail().equalsIgnoreCase(email)).toList();
    }

    /** The set of skills any faculty member has endorsed for this student. */
    public Set<String> endorsedSkills(String studentEmail) {
        Set<String> skills = new LinkedHashSet<>();
        for (Endorsement e : findByStudent(studentEmail)) skills.add(e.skill());
        return skills;
    }

    public boolean alreadyEndorsed(String facultyEmail, String studentEmail, String skill) {
        return endorsements.stream().anyMatch(e ->
                e.facultyEmail().equalsIgnoreCase(facultyEmail)
                        && e.studentEmail().equalsIgnoreCase(studentEmail)
                        && e.skill().equalsIgnoreCase(skill));
    }

    public void add(Endorsement e) {
        if (alreadyEndorsed(e.facultyEmail(), e.studentEmail(), e.skill())) return;
        endorsements.add(e);
        FileManager.appendLine(dataPath, e.toCsv());
    }
}
