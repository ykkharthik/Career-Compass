package model;

/** An internship listing tied to a career domain. Loaded from internships.csv. */
public class Internship {
    private final String domain;
    private final String title;
    private final String organisationType;   // e.g. Product startup, IT services, Research lab
    private final String duration;
    private final String stipendRange;
    private final String prerequisites;

    public Internship(String domain, String title, String organisationType,
                      String duration, String stipendRange, String prerequisites) {
        this.domain = domain;
        this.title = title;
        this.organisationType = organisationType;
        this.duration = duration;
        this.stipendRange = stipendRange;
        this.prerequisites = prerequisites;
    }

    public String getDomain() { return domain; }
    public String getTitle() { return title; }
    public String getPrerequisites() { return prerequisites; }

    public static Internship fromCsv(String line) {
        String[] p = line.split(",", -1);
        return new Internship(p[0], p[1], p[2], p[3], p[4], p[5]);
    }

    @Override
    public String toString() {
        return String.format("%-38s | %-18s | %-10s | %-18s | needs: %s",
                title, organisationType, duration, stipendRange, prerequisites);
    }
}
