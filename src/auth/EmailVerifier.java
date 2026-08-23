package auth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Email format validation + OTP delivery.
 *
 * TWO MODES:
 *  1. REAL EMAIL MODE - if data/mail_config.properties exists with valid SMTP
 *     credentials, the OTP is actually emailed to the user via SmtpMailSender.
 *  2. CONSOLE MODE (fallback) - if no config exists or sending fails, the OTP
 *     is printed to the console so the demo still works offline.
 *
 * See data/mail_config.properties.example for setup instructions.
 */
public class EmailVerifier {

    private static final String CONFIG_PATH = "data/mail_config.properties";

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final SecureRandom random = new SecureRandom();
    private SmtpMailSender mailSender;   // null => console mode

    public EmailVerifier() {
        loadConfig();
    }

    private void loadConfig() {
        Path config = Path.of(CONFIG_PATH);
        if (!Files.exists(config)) return;
        try (InputStream is = Files.newInputStream(config)) {
            Properties props = new Properties();
            props.load(is);
            String host = props.getProperty("smtp.host", "").trim();
            String port = props.getProperty("smtp.port", "465").trim();
            String user = props.getProperty("smtp.username", "").trim();
            String pass = props.getProperty("smtp.app_password", "").trim();
            if (!host.isEmpty() && !user.isEmpty() && !pass.isEmpty()) {
                mailSender = new SmtpMailSender(host, Integer.parseInt(port), user, pass);
                System.out.println("[mail] Real email mode enabled (sending via " + host + ")");
            }
        } catch (IOException | NumberFormatException e) {
            System.out.println("[mail] Could not read mail config, using console mode: " + e.getMessage());
        }
    }

    public boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /** Generates a 6-digit OTP and delivers it (real email if configured, else console). */
    public String sendOtp(String email) {
        String otp = String.format("%06d", random.nextInt(1_000_000));

        if (mailSender != null) {
            try {
                mailSender.send(email,
                        "CareerCompass - Your verification code",
                        "Hello,\r\n\r\nYour CareerCompass verification code is: " + otp
                                + "\r\n\r\nEnter this code in the application to verify your email."
                                + "\r\nIf you did not request this, you can ignore this mail.\r\n");
                System.out.println("\n[MAIL] Verification code emailed to " + email
                        + " - check your inbox (and spam folder).\n");
                return otp;
            } catch (IOException e) {
                System.out.println("\n[MAIL] Sending failed (" + e.getMessage()
                        + ") - falling back to console mode.");
            }
        }

        System.out.println("\n[MAIL GATEWAY] Verification code for " + email);
        System.out.println("[MAIL GATEWAY] (Console mode - your OTP is: " + otp + ")\n");
        return otp;
    }

    public boolean verify(String expectedOtp, String enteredOtp) {
        return expectedOtp != null && expectedOtp.equals(enteredOtp == null ? "" : enteredOtp.trim());
    }
}
