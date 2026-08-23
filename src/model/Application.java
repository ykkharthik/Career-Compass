package model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A student's application to a specific internship listing. Recruiters move
 * it through a hiring pipeline: APPLIED -> SHORTLISTED -> INTERVIEW -> OFFER,
 * or REJECTED at any stage.
 */
public record Application(String id, String studentEmail, String domain, String internshipTitle,
                          String status, String appliedAt, String updatedAt) {

    public enum Status { APPLIED, SHORTLISTED, INTERVIEW, OFFER, REJECTED }

    public Application withStatus(String newStatus, String updatedAt) {
        return new Application(id, studentEmail, domain, internshipTitle, newStatus, appliedAt, updatedAt);
    }

    /** CSV: id,studentEmail,domain,internshipTitle(b64),status,appliedAt,updatedAt */
    public String toCsv() {
        String titleB64 = Base64.getEncoder().encodeToString(internshipTitle.getBytes(StandardCharsets.UTF_8));
        return String.join(",", id, studentEmail, domain, titleB64, status, appliedAt, updatedAt);
    }

    public static Application fromCsv(String line) {
        String[] p = line.split(",", -1);
        String title = new String(Base64.getDecoder().decode(p[3]), StandardCharsets.UTF_8);
        return new Application(p[0], p[1], p[2], title, p[4], p[5], p[6]);
    }
}
