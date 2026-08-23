package model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A student's request for guidance from a mentor. Status moves
 * PENDING -> ACCEPTED / DECLINED. Once accepted, the mentor's note carries
 * their reply back to the student.
 */
public record MentorshipRequest(String id, String studentEmail, String mentorEmail, String domain,
                                String message, String status, String mentorNote, String createdAt) {

    public enum Status { PENDING, ACCEPTED, DECLINED }

    public MentorshipRequest withResponse(String newStatus, String note) {
        return new MentorshipRequest(id, studentEmail, mentorEmail, domain, message, newStatus, note, createdAt);
    }

    /** CSV: id,studentEmail,mentorEmail,domain,message(b64),status,note(b64),createdAt */
    public String toCsv() {
        return String.join(",", id, studentEmail, mentorEmail, domain,
                b64(message), status, b64(mentorNote == null ? "" : mentorNote), createdAt);
    }

    public static MentorshipRequest fromCsv(String line) {
        String[] p = line.split(",", -1);
        return new MentorshipRequest(p[0], p[1], p[2], p[3], unb64(p[4]), p[5], unb64(p[6]), p[7]);
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
    private static String unb64(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }
}
