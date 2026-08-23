package service;

import model.Internship;
import model.Student;
import repository.FileManager;

import java.util.ArrayList;
import java.util.List;

/** Loads the internship dataset, filters by domain, flags readiness by skills. */
public class InternshipAdvisor {

    private final List<Internship> internships = new ArrayList<>();

    public InternshipAdvisor(String csvPath) {
        for (String line : FileManager.readLines(csvPath)) {
            try {
                internships.add(Internship.fromCsv(line));
            } catch (RuntimeException ignored) { }
        }
    }

    public List<Internship> forDomain(String domain) {
        List<Internship> out = new ArrayList<>();
        for (Internship i : internships) {
            if (i.getDomain().equalsIgnoreCase(domain)) out.add(i);
        }
        return out;
    }

    public void print(String domain, Student student) {
        List<Internship> list = forDomain(domain);
        System.out.println("\n--- Internship Opportunities: " + domain + " ---");
        if (list.isEmpty()) {
            System.out.println("(no internships on record for this domain)");
            return;
        }
        for (Internship i : list) {
            boolean ready = true;
            for (String pre : i.getPrerequisites().split("\\|")) {
                if (!student.getSkills().contains(pre.trim().toLowerCase())) {
                    ready = false;
                    break;
                }
            }
            System.out.println("  " + (ready ? "[READY]   " : "[UPSKILL] ") + i);
        }
        System.out.println("  ([READY] = your current skills meet the prerequisites)");
    }
}
