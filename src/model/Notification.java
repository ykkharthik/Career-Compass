package model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** An in-app notification. Cross-cuts every role: shortlists, mentorship replies, endorsements. */
public record Notification(String id, String recipientEmail, String message, String link,
                           boolean read, String createdAt) {

    public Notification asRead() {
        return new Notification(id, recipientEmail, message, link, true, createdAt);
    }

    /** CSV: id,recipientEmail,message(b64),link,read,createdAt */
    public String toCsv() {
        String msgB64 = Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
        return String.join(",", id, recipientEmail, msgB64, link, String.valueOf(read), createdAt);
    }

    public static Notification fromCsv(String line) {
        String[] p = line.split(",", -1);
        String msg = new String(Base64.getDecoder().decode(p[2]), StandardCharsets.UTF_8);
        return new Notification(p[0], p[1], msg, p[3], Boolean.parseBoolean(p[4]), p[5]);
    }
}
