package auth;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A minimal SMTP client written in pure Java (no external libraries).
 * Speaks the SMTP protocol directly over an SSL socket (implicit TLS,
 * port 465 - e.g. smtp.gmail.com:465).
 *
 * Flow: connect -> EHLO -> AUTH LOGIN -> MAIL FROM -> RCPT TO -> DATA -> QUIT.
 */
public class SmtpMailSender {

    private final String host;
    private final int port;
    private final String username;   // full email address of the sending account
    private final String appPassword;

    public SmtpMailSender(String host, int port, String username, String appPassword) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.appPassword = appPassword;
    }

    /**
     * Sends a plain-text email. Throws IOException with the server's reply on
     * any failure so the caller can fall back gracefully.
     */
    public void send(String to, String subject, String body) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
            socket.setSoTimeout(15_000);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            expect(in, "220");                                   // server greeting
            command(out, in, "EHLO careercompass.local", "250");
            command(out, in, "AUTH LOGIN", "334");
            command(out, in, b64(username), "334");
            command(out, in, b64(appPassword), "235");           // 235 = authenticated
            command(out, in, "MAIL FROM:<" + username + ">", "250");
            command(out, in, "RCPT TO:<" + to + ">", "250");
            command(out, in, "DATA", "354");

            String message =
                    "From: CareerCompass <" + username + ">\r\n" +
                    "To: <" + to + ">\r\n" +
                    "Subject: " + subject + "\r\n" +
                    "MIME-Version: 1.0\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "\r\n" +
                    dotStuff(body) + "\r\n.";
            command(out, in, message, "250");
            command(out, in, "QUIT", "221");
        }
    }

    private void command(BufferedWriter out, BufferedReader in,
                         String cmd, String expectedCode) throws IOException {
        out.write(cmd + "\r\n");
        out.flush();
        expect(in, expectedCode);
    }

    /** Reads (possibly multi-line) SMTP reply and checks the status code. */
    private void expect(BufferedReader in, String expectedCode) throws IOException {
        String line;
        String lastLine = "";
        do {
            line = in.readLine();
            if (line == null) throw new IOException("Connection closed by mail server");
            lastLine = line;
        } while (line.length() >= 4 && line.charAt(3) == '-');   // "250-..." = more lines follow

        if (!lastLine.startsWith(expectedCode)) {
            throw new IOException("Mail server said: " + lastLine);
        }
    }

    /**
     * RFC 5321 transparency: a body line that starts with '.' must be sent as
     * '..', otherwise the server would read it as the end-of-message marker.
     */
    private static String dotStuff(String body) {
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\r?\n", -1)) {
            if (sb.length() > 0) sb.append("\r\n");
            sb.append(line.startsWith(".") ? "." + line : line);
        }
        return sb.toString();
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
