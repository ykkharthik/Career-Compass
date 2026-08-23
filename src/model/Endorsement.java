package model;

/**
 * A faculty member vouching that a student genuinely has a given skill.
 * Endorsed skills carry extra weight in the recommendation engine and are
 * shown to recruiters as a trust signal.
 */
public record Endorsement(String facultyEmail, String studentEmail, String skill, String createdAt) {

    public String toCsv() {
        return String.join(",", facultyEmail, studentEmail, skill.toLowerCase(), createdAt);
    }

    public static Endorsement fromCsv(String line) {
        String[] p = line.split(",", -1);
        return new Endorsement(p[0], p[1], p[2], p[3]);
    }
}
