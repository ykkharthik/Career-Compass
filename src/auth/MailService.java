package auth;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Decides how an OTP reaches the user.
 *
 * If data/mail.properties supplies SMTP credentials, the code is sent as a real
 * email through {@link SmtpMailSender} (pure JDK, no mail library). If the file
 * is absent or the send fails, the app falls back to DEMO MODE and shows the
 * code on screen, so the flow is always demonstrable.
 *
 * data/mail.properties:
 *     smtp.host=smtp.gmail.com
 *     smtp.port=465
 *     smtp.user=youraddress@gmail.com
 *     smtp.pass=your-16-char-app-password
 *
 * For Gmail: turn on 2-step verification, then create an App Password at
 * myaccount.google.com/apppasswords and paste it as smtp.pass. A normal Gmail
 * password will be rejected.
 */
public class MailService {

    private static final String CONFIG_PATH = "data/mail.properties";

    private final SmtpMailSender sender;   // null => demo mode
    private String lastError;

    public MailService() {
        SmtpMailSender s = null;
        if (Files.exists(Path.of(CONFIG_PATH))) {
            Properties config = new Properties();
            try (FileInputStream in = new FileInputStream(CONFIG_PATH)) {
                config.load(in);
                String host = config.getProperty("smtp.host");
                String user = config.getProperty("smtp.user");
                String pass = config.getProperty("smtp.pass");
                int port = Integer.parseInt(config.getProperty("smtp.port", "465").trim());
                if (notBlank(host) && notBlank(user) && notBlank(pass)) {
                    s = new SmtpMailSender(host.trim(), port, user.trim(), pass.trim());
                }
            } catch (IOException | NumberFormatException e) {
                lastError = "Could not read " + CONFIG_PATH + ": " + e.getMessage();
            }
        }
        this.sender = s;
    }

    public boolean isConfigured() {
        return sender != null;
    }

    /** Why the last send failed — shown in the demo-mode banner. */
    public String getLastError() {
        return lastError;
    }

    /**
     * Tries to email the code.
     *
     * @return true if the mail server accepted the message; false means the
     *         caller should fall back to showing the code on screen.
     */
    public boolean sendOtpEmail(String to, String otp) {
        if (sender == null) {
            lastError = "No data/mail.properties found";
            return false;
        }
        try {
            sender.send(to,
                    "Your CareerCompass verification code",
                    "Your one-time verification code is: " + otp + "\r\n\r\n"
                  + "Enter it on the verification page to finish creating your account.\r\n"
                  + "The code expires when you close the registration page.\r\n\r\n"
                  + "If you did not request this, you can ignore this email.");
            lastError = null;
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
            System.out.println("[mail] SMTP send failed (" + lastError
                    + ") - falling back to demo mode");
            return false;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
