package web;

import auth.AuthService;
import auth.MailService;
import auth.User;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static web.Pages.esc;

/**
 * Everything a signed-out visitor can reach: the public landing page, and
 * the sign-in / register / verify-email flow. Extracted out of
 * {@link WebServer} so the auth story — the first thing anyone (including a
 * recruiter clicking a demo link) sees — lives in one focused file instead
 * of being interleaved with all five roles' dashboard code.
 */
public final class AuthPages {

    private final AuthService auth;
    private final MailService mail;
    private final SessionManager sessions;
    private final AppContext ctx;

    public AuthPages(AuthService auth, MailService mail, SessionManager sessions, AppContext ctx) {
        this.auth = auth;
        this.mail = mail;
        this.sessions = sessions;
        this.ctx = ctx;
    }

    // ------------------------------ landing ------------------------------

    public void landingPage(HttpExchange ex) throws IOException {
        User u = ctx.currentUser(ex);
        if (u != null) { Http.redirect(ex, ctx.homeFor(u)); return; }

        String body = "<div class=\"hero\">"
                + "<span class=\"eyebrow\">Career guidance platform</span>"
                + "<h1>Chart your course from CGPA and skills to your best-fit career.</h1>"
                + "<p class=\"lede\">CareerCompass ranks six career domains against a student's profile "
                + "using a hybrid engine, combining transparent rules with two from-scratch machine "
                + "learning models, then closes the loop with internships, mentorship, and "
                + "faculty-verified skills. Five roles, one shared platform.</p>"
                + "<div class=\"cta-row\">"
                + "<a class=\"btn brass\" href=\"/register\">Create an account</a>"
                + "<a class=\"btn ghost\" href=\"/login\">Sign in</a></div></div>"

                + "<h2>How it works</h2>"
                + "<div class=\"steps\">"
                + step("01", "Build a profile", "CGPA, self-reported skills, and five interest ratings, "
                    + "visualised as a radar chart.")
                + step("02", "Get ranked, explained", "Six domains scored 50% rule-based, 25% k-NN, "
                    + "25% Naive Bayes, each with the reasons and a peer percentile.")
                + step("03", "Close the loop", "Apply to internships, request mentorship, and get skills "
                    + "faculty-endorsed, every step notifies you.")
                + "</div>"

                + "<h2>Five roles, one platform</h2>"
                + "<div class=\"roles-grid\">"
                + role("Student", "Profile, recommendations, applications")
                + role("Recruiter", "Candidate search, shortlisting, pipeline")
                + role("Mentor", "Set up a profile, accept requests")
                + role("Faculty", "Endorse verified student skills")
                + role("Admin", "Platform-wide account management")
                + "</div>";

        Http.html(ex, 200, Pages.shell("Home", Pages.LOGGED_OUT_NAV, body));
    }

    private static String step(String num, String title, String desc) {
        return "<div class=\"step\"><span class=\"num\">" + num + "</span>"
                + "<h3>" + title + "</h3><p>" + desc + "</p></div>";
    }

    private static String role(String name, String desc) {
        return "<div class=\"role\"><b>" + name + "</b><span>" + desc + "</span></div>";
    }

    // ------------------------------ login ------------------------------

    public void loginPage(HttpExchange ex, String error) throws IOException {
        User u = ctx.currentUser(ex);
        if (u != null) { Http.redirect(ex, ctx.homeFor(u)); return; }

        String body = "<div class=\"card narrow\"><h1>Sign in</h1>"
                + "<p class=\"sub\">Students, recruiters, mentors, faculty and admins all sign in here.</p>"
                + Pages.errorBox(error)
                + "<form method=\"post\" action=\"/login\">"
                + "<label for=\"email\">Email</label><input id=\"email\" name=\"email\" type=\"email\" required>"
                + "<label for=\"pw\">Password</label><input id=\"pw\" name=\"password\" type=\"password\" required>"
                + "<button>Sign in</button></form>"
                + "<p style=\"margin-top:1rem\">New here? <a href=\"/register\">Create an account</a></p>"
                + "<p style=\"margin-top:.4rem\"><a href=\"/\">← Back home</a></p></div>";
        Http.html(ex, 200, Pages.shell("Sign in", Pages.LOGGED_OUT_NAV, body));
    }

    public void doLogin(HttpExchange ex) throws IOException {
        Map<String, String> f = Http.form(ex);
        AuthService.LoginResult result = auth.attemptLogin(f.get("email"), f.get("password"));
        switch (result.outcome()) {
            case OK -> signIn(ex, result.user());
            case LOCKED -> loginPage(ex, "Too many failed attempts. This account is locked for "
                    + result.lockedForSeconds() + " more seconds.");
            case UNVERIFIED -> loginPage(ex, "This account's email was never verified.");
            default -> loginPage(ex, "Email or password is incorrect.");
        }
    }

    private void signIn(HttpExchange ex, User user) throws IOException {
        String token = sessions.create(user.getEmail());
        ex.getResponseHeaders().add("Set-Cookie",
                "cc_session=" + token + "; HttpOnly; Path=/; SameSite=Lax");
        Http.redirect(ex, ctx.homeFor(user));
    }

    public void doLogout(HttpExchange ex) throws IOException {
        sessions.destroy(Http.cookie(ex));
        ex.getResponseHeaders().add("Set-Cookie", "cc_session=deleted; Max-Age=0; Path=/");
        Http.redirect(ex, "/");
    }

    // ------------------------------ register ------------------------------

    public void registerPage(HttpExchange ex, String error) throws IOException {
        String body = "<div class=\"card narrow\"><h1>Create your account</h1>"
                + "<p class=\"sub\">One account per email id. A verification code confirms it's yours.</p>"
                + Pages.errorBox(error)
                + "<form method=\"post\" action=\"/register\">"
                + "<label for=\"email\">Email</label><input id=\"email\" name=\"email\" type=\"email\" required>"
                + "<label for=\"pw\">Password (min 6 characters)</label>"
                + "<input id=\"pw\" name=\"password\" type=\"password\" minlength=\"6\" required>"
                + "<label for=\"role\">I am a</label><select id=\"role\" name=\"role\">"
                + "<option value=\"STUDENT\">Student</option>"
                + "<option value=\"RECRUITER\">Recruiter</option>"
                + "<option value=\"MENTOR\">Mentor (industry professional)</option>"
                + "<option value=\"FACULTY\">Faculty / academic advisor</option></select>"
                + "<button class=\"brass\">Send verification code</button></form>"
                + "<p style=\"margin-top:1rem\">Already registered? <a href=\"/login\">Sign in</a></p>"
                + "<p style=\"margin-top:.4rem\"><a href=\"/\">← Back home</a></p></div>";
        Http.html(ex, 200, Pages.shell("Register", Pages.LOGGED_OUT_NAV, body));
    }

    public void doRegister(HttpExchange ex) throws IOException {
        Map<String, String> f = Http.form(ex);
        String email = f.getOrDefault("email", "").trim().toLowerCase();
        User.Role role = switch (f.getOrDefault("role", "STUDENT")) {
            case "RECRUITER" -> User.Role.RECRUITER;
            case "MENTOR" -> User.Role.MENTOR;
            case "FACULTY" -> User.Role.FACULTY;
            default -> User.Role.STUDENT;
        };
        String error = auth.beginRegistration(email, f.get("password"), role);
        if (error != null) { registerPage(ex, error); return; }

        AuthService.Pending p = auth.getPending(email);
        boolean sent = mail.sendOtpEmail(email, p.otp());
        Http.redirect(ex, "/verify?email=" + email + (sent ? "" : "&demo=1"));
    }

    // ------------------------------ verify ------------------------------

    public void verifyPage(HttpExchange ex, String email, String error) throws IOException {
        boolean demo = Http.q(ex).containsKey("demo");
        AuthService.Pending p = auth.getPending(email);
        String note;
        if (p == null) {
            note = "";
        } else if (demo) {
            note = Pages.noteBox("<b>Demo mode</b> — no mail server is configured, so your code is shown "
                    + "here instead of being emailed: <b style=\"font-size:1.2rem;letter-spacing:.15em\">"
                    + p.otp() + "</b><br><span style=\"font-size:.85rem\">To send real emails, create "
                    + "<code>data/mail.properties</code> with your SMTP details (see README).</span>");
        } else {
            note = Pages.noteBox("We emailed a 6-digit code to <b>" + esc(email) + "</b>. Enter it below.");
        }
        String body = "<div class=\"card narrow\"><h1>Verify your email</h1>"
                + "<p class=\"sub\">This confirms the address belongs to you.</p>"
                + note + Pages.errorBox(error)
                + "<form method=\"post\" action=\"/verify\">"
                + "<input type=\"hidden\" name=\"email\" value=\"" + esc(email) + "\">"
                + (demo ? "<input type=\"hidden\" name=\"demo\" value=\"1\">" : "")
                + "<label for=\"otp\">Verification code</label>"
                + "<input id=\"otp\" name=\"otp\" inputmode=\"numeric\" pattern=\"[0-9]{6}\" required>"
                + "<button class=\"brass\">Verify and create account</button></form></div>";
        Http.html(ex, 200, Pages.shell("Verify email", Pages.LOGGED_OUT_NAV, body));
    }

    private void verifyPageInline(HttpExchange ex, String email, boolean demo, String error)
            throws IOException {
        AuthService.Pending p = auth.getPending(email);
        String note = (p != null && demo)
                ? Pages.noteBox("<b>Demo mode</b> — your code: <b style=\"font-size:1.2rem;"
                + "letter-spacing:.15em\">" + p.otp() + "</b>")
                : "";
        String body = "<div class=\"card narrow\"><h1>Verify your email</h1>"
                + note + Pages.errorBox(error)
                + "<form method=\"post\" action=\"/verify\">"
                + "<input type=\"hidden\" name=\"email\" value=\"" + esc(email) + "\">"
                + (demo ? "<input type=\"hidden\" name=\"demo\" value=\"1\">" : "")
                + "<label for=\"otp\">Verification code</label>"
                + "<input id=\"otp\" name=\"otp\" required>"
                + "<button class=\"brass\">Verify and create account</button></form></div>";
        Http.html(ex, 200, Pages.shell("Verify email", Pages.LOGGED_OUT_NAV, body));
    }

    public void doVerify(HttpExchange ex) throws IOException {
        Map<String, String> f = Http.form(ex);
        Optional<User> user = auth.completeRegistration(f.get("email"), f.get("otp"));
        if (user.isEmpty()) {
            verifyPageInline(ex, f.get("email"), "1".equals(f.get("demo")),
                    "That code is not correct. Check it and try again.");
            return;
        }
        signIn(ex, user.get());
    }
}
