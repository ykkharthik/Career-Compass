package test;

import auth.AuthService;
import auth.User;

import java.io.File;
import java.util.Optional;

/**
 * Each test uses its own scratch CSV file (never data/users.csv) so tests
 * can't interfere with each other or with real account data, and cleans up
 * after itself even if an assertion fails.
 */
public class AuthServiceTest {

    public void testRegistrationAndLoginRoundTrip() {
        withScratchFile("round_trip", path -> {
            AuthService auth = new AuthService(path);
            String email = "student@example.com";

            Assert.equal("beginRegistration should succeed", null,
                    auth.beginRegistration(email, "correcthorse", User.Role.STUDENT));
            String otp = auth.getPending(email).otp();
            Optional<User> created = auth.completeRegistration(email, otp);
            Assert.isTrue("account should be created after correct OTP", created.isPresent());

            AuthService.LoginResult ok = auth.attemptLogin(email, "correcthorse");
            Assert.equal("correct password should log in", AuthService.LoginOutcome.OK, ok.outcome());

            AuthService.LoginResult bad = auth.attemptLogin(email, "wrongpassword");
            Assert.equal("wrong password should be rejected", AuthService.LoginOutcome.BAD_CREDENTIALS, bad.outcome());
        });
    }

    public void testWrongOtpDoesNotCreateAccount() {
        withScratchFile("wrong_otp", path -> {
            AuthService auth = new AuthService(path);
            String email = "student2@example.com";
            auth.beginRegistration(email, "correcthorse", User.Role.STUDENT);

            Optional<User> result = auth.completeRegistration(email, "000000");
            Assert.isFalse("a wrong OTP must not create the account", result.isPresent());
        });
    }

    public void testAccountLocksAfterFiveFailedAttempts() {
        withScratchFile("lockout", path -> {
            AuthService auth = new AuthService(path);
            String email = "student3@example.com";
            auth.beginRegistration(email, "correcthorse", User.Role.STUDENT);
            auth.completeRegistration(email, auth.getPending(email).otp());

            AuthService.LoginResult last = null;
            for (int i = 0; i < 5; i++) last = auth.attemptLogin(email, "wrongpassword");

            Assert.equal("the 5th consecutive failed attempt should lock the account",
                    AuthService.LoginOutcome.LOCKED, last.outcome());

            AuthService.LoginResult evenWithCorrectPassword = auth.attemptLogin(email, "correcthorse");
            Assert.equal("a locked account rejects even the correct password",
                    AuthService.LoginOutcome.LOCKED, evenWithCorrectPassword.outcome());
        });
    }

    public void testPasswordsAreNeverStoredInPlainText() {
        withScratchFile("hashing", path -> {
            AuthService auth = new AuthService(path);
            String email = "student4@example.com";
            String plainPassword = "correcthorse";
            auth.beginRegistration(email, plainPassword, User.Role.STUDENT);
            auth.completeRegistration(email, auth.getPending(email).otp());

            User stored = auth.findByEmail(email).orElseThrow();
            Assert.isFalse("the stored hash must not equal the plain password",
                    plainPassword.equals(stored.getPasswordHash()));
            Assert.isFalse("the salt must not be empty", stored.getSalt() == null || stored.getSalt().isBlank());
        });
    }

    private interface ScratchTest { void run(String scratchFilePath); }

    private void withScratchFile(String name, ScratchTest test) {
        String path = "data/.test_auth_" + name + ".csv";
        File file = new File(path);
        try {
            test.run(path);
        } finally {
            file.delete();
        }
    }
}
