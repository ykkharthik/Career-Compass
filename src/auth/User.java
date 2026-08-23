package auth;

/**
 * Represents an authenticated account. Passwords are never stored in plain
 * text — only a salted SHA-256 hash. Tracks failed-login state so the login
 * flow can lock an account out after repeated bad attempts.
 */
public class User {

    public enum Role { STUDENT, RECRUITER, MENTOR, FACULTY, ADMIN }

    private final String email;
    private final String passwordHash;
    private final String salt;
    private final Role role;
    private boolean verified;
    private int failedAttempts;
    private long lockedUntilEpochMs;   // 0 = not locked

    public User(String email, String passwordHash, String salt, Role role, boolean verified) {
        this(email, passwordHash, salt, role, verified, 0, 0L);
    }

    public User(String email, String passwordHash, String salt, Role role, boolean verified,
               int failedAttempts, long lockedUntilEpochMs) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.verified = verified;
        this.failedAttempts = failedAttempts;
        this.lockedUntilEpochMs = lockedUntilEpochMs;
    }

    public String getEmail()        { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt()         { return salt; }
    public Role getRole()           { return role; }
    public boolean isVerified()     { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public int getFailedAttempts()  { return failedAttempts; }
    public void setFailedAttempts(int n) { this.failedAttempts = n; }
    public long getLockedUntilEpochMs()  { return lockedUntilEpochMs; }
    public void setLockedUntilEpochMs(long t) { this.lockedUntilEpochMs = t; }
    public boolean isLocked()       { return System.currentTimeMillis() < lockedUntilEpochMs; }

    /** CSV: email,hash,salt,role,verified,failedAttempts,lockedUntilEpochMs */
    public String toCsv() {
        return String.join(",", email, passwordHash, salt, role.name(),
                String.valueOf(verified), String.valueOf(failedAttempts),
                String.valueOf(lockedUntilEpochMs));
    }

    public static User fromCsv(String line) {
        String[] p = line.split(",", -1);
        int failed = p.length > 5 ? parseIntSafe(p[5]) : 0;
        long lockedUntil = p.length > 6 ? parseLongSafe(p[6]) : 0L;
        return new User(p[0], p[1], p[2], Role.valueOf(p[3]), Boolean.parseBoolean(p[4]),
                failed, lockedUntil);
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }
    private static long parseLongSafe(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0L; }
    }
}
