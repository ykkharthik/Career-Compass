package model;

/** A faculty/academic-advisor profile: department affiliation. */
public record Faculty(String email, String name, String department) {

    public String toCsv() {
        return String.join(",", email, name, department);
    }

    public static Faculty fromCsv(String line) {
        String[] p = line.split(",", -1);
        return new Faculty(p[0], p[1], p[2]);
    }
}
