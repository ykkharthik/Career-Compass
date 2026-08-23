package model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A mentor's public profile: which domain they mentor in, a short bio, and
 * years of industry experience. One profile per login account.
 */
public record Mentor(String email, String name, String domain, String bio, int yearsExperience) {

    /** CSV: email,name,domain,bio(base64),years */
    public String toCsv() {
        String bioB64 = Base64.getEncoder().encodeToString(bio.getBytes(StandardCharsets.UTF_8));
        return String.join(",", email, name, domain, bioB64, String.valueOf(yearsExperience));
    }

    public static Mentor fromCsv(String line) {
        String[] p = line.split(",", -1);
        String bio = new String(Base64.getDecoder().decode(p[3]), StandardCharsets.UTF_8);
        return new Mentor(p[0], p[1], p[2], bio, Integer.parseInt(p[4]));
    }
}
