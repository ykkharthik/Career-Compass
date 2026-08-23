package auth;

import repository.FileManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Registration + login with unique email enforcement, salted SHA-256 password
 * hashing, and OTP-based email verification. Accounts persist to users.csv.
 */
public class AuthService {

    private final String userFilePath;
    private final List<User> users = new ArrayList<>();
    private final EmailVerifier emailVerifier = new EmailVerifier();
    private final SecureRandom random = new SecureRandom();

    public AuthService(String userFilePath) {
        this.userFilePath = userFilePath;
        for (String line : FileManager.readLines(userFilePath)) {
            if (!line.isBlank()) users.add(User.fromCsv(line));
        }
    }

    public Optional<User> findByEmail(String email) {
        return users.stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email.trim()))
                .findFirst();
    }

    /** Full interactive registration flow. Returns the new user, or empty on failure. */
    public Optional<User> register(Scanner in, User.Role role) {
        System.out.print("Enter email id: ");
        String email = in.nextLine().trim().toLowerCase();

        if (!emailVerifier.isValidEmail(email)) {
            System.out.println("Invalid email format. Registration cancelled.");
            return Optional.empty();
        }
        if (findByEmail(email).isPresent()) {
            System.out.println("An account with this email already exists. Please log in instead.");
            return Optional.empty();
        }

        System.out.print("Create a password (min 6 characters): ");
        String password = in.nextLine();
        if (password.length() < 6) {
            System.out.println("Password too short. Registration cancelled.");
            return Optional.empty();
        }

        // Email verification via OTP (3 attempts).
        String otp = emailVerifier.sendOtp(email);
        boolean verified = false;
        for (int attempt = 1; attempt <= 3 && !verified; attempt++) {
            System.out.print("Enter the 6-digit code sent to your email (attempt " + attempt + "/3): ");
            verified = emailVerifier.verify(otp, in.nextLine());
            if (!verified) System.out.println("Incorrect code.");
        }
        if (!verified) {
            System.out.println("Email could not be verified. Registration cancelled.");
            return Optional.empty();
        }

        String salt = newSalt();
        User user = new User(email, hash(password, salt), salt, role, true);
        users.add(user);
        save();
        System.out.println("Account created and email verified. Welcome, " + email + "!");
        return Optional.of(user);
    }

    /** Interactive login. Returns the user on success. */
    public Optional<User> login(Scanner in) {
        System.out.print("Email: ");
        String email = in.nextLine().trim().toLowerCase();
        System.out.print("Password: ");
        String password = in.nextLine();

        Optional<User> found = findByEmail(email);
        if (found.isEmpty()) {
            System.out.println("No account found for that email.");
            return Optional.empty();
        }
        User user = found.get();
        if (!user.getPasswordHash().equals(hash(password, user.getSalt()))) {
            System.out.println("Incorrect password.");
            return Optional.empty();
        }
        if (!user.isVerified()) {
            System.out.println("This account's email was never verified.");
            return Optional.empty();
        }
        System.out.println("Login successful (" + user.getRole() + ").");
        return Optional.of(user);
    }


    // ----------------- Web-friendly (non-interactive) API -----------------

    /** A registration awaiting OTP confirmation. */
    public record Pending(String email, String password, User.Role role, String otp) {}

    private final java.util.Map<String, Pending> pending = new java.util.HashMap<>();

    /** Validates input and generates an OTP. Returns an error message, or null on success. */
    public String beginRegistration(String email, String password, User.Role role) {
        email = email == null ? "" : email.trim().toLowerCase();
        if (!emailVerifier.isValidEmail(email)) return "That email address is not valid.";
        if (findByEmail(email).isPresent()) return "An account with this email already exists. Log in instead.";
        if (password == null || password.length() < 6) return "Password must be at least 6 characters.";
        String otp = String.format("%06d", random.nextInt(1_000_000));
        pending.put(email, new Pending(email, password, role, otp));
        return null;
    }

    public Pending getPending(String email) {
        return pending.get(email == null ? "" : email.trim().toLowerCase());
    }

    /** Confirms the OTP and creates the account. Returns the user, or empty if the code is wrong. */
    public java.util.Optional<User> completeRegistration(String email, String enteredOtp) {
        Pending p = getPending(email);
        if (p == null || !p.otp().equals(enteredOtp == null ? "" : enteredOtp.trim()))
            return java.util.Optional.empty();
        pending.remove(p.email());
        String salt = newSalt();
        User user = new User(p.email(), hash(p.password(), salt), salt, p.role(), true);
        users.add(user);
        save();
        return java.util.Optional.of(user);
    }

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MS = 5 * 60 * 1000L; // 5 minutes

    /** Result of a login attempt, distinguishing failure reasons for the UI. */
    public enum LoginOutcome { OK, BAD_CREDENTIALS, LOCKED, UNVERIFIED }

    public record LoginResult(LoginOutcome outcome, User user, long lockedForSeconds) {}

    /**
     * Non-interactive credential check for the web login form, with
     * brute-force lockout: MAX_ATTEMPTS wrong passwords locks the account
     * for LOCK_DURATION_MS. A correct password resets the counter.
     */
    public LoginResult attemptLogin(String email, String password) {
        Optional<User> found = findByEmail(email == null ? "" : email);
        if (found.isEmpty()) return new LoginResult(LoginOutcome.BAD_CREDENTIALS, null, 0);
        User user = found.get();

        if (user.isLocked()) {
            long secondsLeft = (user.getLockedUntilEpochMs() - System.currentTimeMillis()) / 1000;
            return new LoginResult(LoginOutcome.LOCKED, null, Math.max(1, secondsLeft));
        }

        boolean correct = user.getPasswordHash().equals(hash(password == null ? "" : password, user.getSalt()));
        if (!correct) {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= MAX_ATTEMPTS) {
                user.setLockedUntilEpochMs(System.currentTimeMillis() + LOCK_DURATION_MS);
                user.setFailedAttempts(0);
                save();
                return new LoginResult(LoginOutcome.LOCKED, null, LOCK_DURATION_MS / 1000);
            }
            save();
            return new LoginResult(LoginOutcome.BAD_CREDENTIALS, null, 0);
        }

        if (!user.isVerified()) return new LoginResult(LoginOutcome.UNVERIFIED, null, 0);

        if (user.getFailedAttempts() != 0 || user.getLockedUntilEpochMs() != 0) {
            user.setFailedAttempts(0);
            user.setLockedUntilEpochMs(0);
            save();
        }
        return new LoginResult(LoginOutcome.OK, user, 0);
    }

    /** Non-interactive credential check for the web login form. */
    public java.util.Optional<User> checkCredentials(String email, String password) {
        return findByEmail(email == null ? "" : email)
                .filter(u -> u.getPasswordHash().equals(hash(password == null ? "" : password, u.getSalt())))
                .filter(User::isVerified);
    }

    public java.util.List<User> allUsers() {
        return new java.util.ArrayList<>(users);
    }

    public boolean deleteUser(String email) {
        boolean removed = users.removeIf(u -> u.getEmail().equalsIgnoreCase(email)
                && u.getRole() != User.Role.ADMIN);
        if (removed) save();
        return removed;
    }

    /** Ensures a built-in admin account exists (email: admin@careercompass.com, password: admin123). */
    public void ensureAdminAccount() {
        if (findByEmail("admin@careercompass.com").isEmpty()) {
            String salt = newSalt();
            users.add(new User("admin@careercompass.com", hash("admin123", salt), salt,
                    User.Role.ADMIN, true));
            save();
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        for (User u : users) lines.add(u.toCsv());
        FileManager.writeLines(userFilePath, lines);
    }

    private String newSalt() {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
