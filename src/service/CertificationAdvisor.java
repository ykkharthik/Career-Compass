package service;

import model.Certification;
import repository.FileManager;

import java.util.ArrayList;
import java.util.List;

/** Loads the certification dataset and filters it by career domain. */
public class CertificationAdvisor {

    private final List<Certification> certifications = new ArrayList<>();

    public CertificationAdvisor(String csvPath) {
        for (String line : FileManager.readLines(csvPath)) {
            try {
                certifications.add(Certification.fromCsv(line));
            } catch (RuntimeException ignored) { }
        }
    }

    public List<Certification> forDomain(String domain) {
        List<Certification> out = new ArrayList<>();
        for (Certification c : certifications) {
            if (c.getDomain().equalsIgnoreCase(domain)) out.add(c);
        }
        return out;
    }

    public void print(String domain) {
        List<Certification> list = forDomain(domain);
        System.out.println("\n--- Recommended Certifications: " + domain + " ---");
        if (list.isEmpty()) {
            System.out.println("(no certifications on record for this domain)");
            return;
        }
        for (Certification c : list) System.out.println("  " + c);
    }
}
