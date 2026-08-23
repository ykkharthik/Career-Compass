package model;

import exception.InvalidProfileException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A student profile: identity, academics, skills, and interest ratings (1-5)
 * across five dimensions used by both the rule engine and the k-NN model.
 */
public class Student {

    private final String email;          // links profile to login account
    private String name;
    private double cgpa;
    private Set<String> skills = new LinkedHashSet<>();

    // Interest ratings 1..5 : coding, math/stats, design/creativity, communication, security
    private int interestCoding;
    private int interestMath;
    private int interestDesign;
    private int interestCommunication;
    private int interestSecurity;

    public Student(String email, String name, double cgpa, Set<String> skills,
                   int coding, int math, int design, int communication, int security)
            throws InvalidProfileException {
        if (name == null || name.isBlank())
            throw new InvalidProfileException("Name cannot be empty.");
        if (cgpa < 0 || cgpa > 10)
            throw new InvalidProfileException("CGPA must be between 0 and 10.");
        for (int r : new int[]{coding, math, design, communication, security})
            if (r < 1 || r > 5)
                throw new InvalidProfileException("Interest ratings must be between 1 and 5.");

        this.email = email.toLowerCase();
        this.name = name.trim();
        this.cgpa = cgpa;
        this.skills = skills.stream()
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        this.interestCoding = coding;
        this.interestMath = math;
        this.interestDesign = design;
        this.interestCommunication = communication;
        this.interestSecurity = security;
    }

    public String getEmail()      { return email; }
    public String getName()       { return name; }
    public double getCgpa()       { return cgpa; }
    public Set<String> getSkills(){ return skills; }
    public int getInterestCoding()        { return interestCoding; }
    public int getInterestMath()          { return interestMath; }
    public int getInterestDesign()        { return interestDesign; }
    public int getInterestCommunication() { return interestCommunication; }
    public int getInterestSecurity()      { return interestSecurity; }

    /** Feature vector used by the k-NN classifier: 5 interests + scaled CGPA. */
    public double[] toFeatureVector() {
        return new double[]{
                interestCoding, interestMath, interestDesign,
                interestCommunication, interestSecurity,
                cgpa / 2.0   // scale 0-10 CGPA into roughly the same 0-5 range
        };
    }

    /** CSV: email,name,cgpa,skill1|skill2,coding,math,design,comm,security */
    public String toCsv() {
        return String.join(",",
                email, name, String.valueOf(cgpa), String.join("|", skills),
                String.valueOf(interestCoding), String.valueOf(interestMath),
                String.valueOf(interestDesign), String.valueOf(interestCommunication),
                String.valueOf(interestSecurity));
    }

    public static Student fromCsv(String line) throws InvalidProfileException {
        String[] p = line.split(",", -1);
        Set<String> skills = new LinkedHashSet<>();
        if (!p[3].isBlank()) skills.addAll(Arrays.asList(p[3].split("\\|")));
        return new Student(p[0], p[1], Double.parseDouble(p[2]), skills,
                Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]),
                Integer.parseInt(p[7]), Integer.parseInt(p[8]));
    }

    @Override
    public String toString() {
        return String.format("%s | CGPA %.2f | skills: %s", name, cgpa,
                skills.isEmpty() ? "(none)" : String.join(", ", skills));
    }
}
