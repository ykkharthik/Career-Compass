package model;

/** A certification suggestion tied to a career domain. Loaded from certifications.csv. */
public class Certification {
    private final String domain;
    private final String name;
    private final String provider;
    private final String level;   // Beginner / Intermediate / Advanced

    public Certification(String domain, String name, String provider, String level) {
        this.domain = domain;
        this.name = name;
        this.provider = provider;
        this.level = level;
    }

    public String getDomain()   { return domain; }
    public String getName()     { return name; }
    public String getProvider() { return provider; }
    public String getLevel()    { return level; }

    public static Certification fromCsv(String line) {
        String[] p = line.split(",", -1);
        return new Certification(p[0], p[1], p[2], p[3]);
    }

    @Override
    public String toString() {
        return String.format("%-52s | %-22s | %s", name, provider, level);
    }
}
