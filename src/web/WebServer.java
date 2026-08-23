package web;

import auth.AuthService;
import auth.MailService;
import auth.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ml.KnnCareerClassifier;
import ml.NaiveBayesClassifier;
import model.CareerPath;
import model.CloudEngineering;
import model.Cybersecurity;
import model.DataScience;
import model.ProductManagement;
import model.SoftwareEngineering;
import model.Student;
import model.UiUxDesign;
import repository.ApplicationRepository;
import repository.EndorsementRepository;
import repository.FacultyRepository;
import repository.MentorRepository;
import repository.MentorshipRepository;
import repository.NotificationRepository;
import repository.StudentRepository;
import service.CertificationAdvisor;
import service.InternshipAdvisor;
import service.PercentileService;
import service.RecommendationService;
import service.SkillGapService;
import service.TrendsService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static web.Pages.esc;

/**
 * CareerCompass web application — served entirely by the JDK's built-in
 * HttpServer (no frameworks, no external libraries). Run WebMain, then open
 * http://localhost:8080
 *
 * Five roles share this server: Student, Recruiter, Mentor, Faculty, Admin.
 * State-changing POST routes for authenticated users are protected by a
 * per-session CSRF token (see SessionManager); the pre-login auth routes
 * (login/register/verify) instead rely on OTP verification and the account
 * lockout in AuthService.
 */
public class WebServer {

    private final AuthService auth = new AuthService("data/users.csv");
    private final MailService mail = new MailService();
    private final SessionManager sessions = new SessionManager();
    private final StudentRepository students = new StudentRepository("data/students.csv");

    private final List<CareerPath> careers = List.of(
            new SoftwareEngineering(), new DataScience(), new Cybersecurity(),
            new CloudEngineering(), new UiUxDesign(), new ProductManagement());
    private final KnnCareerClassifier classifier =
            new KnnCareerClassifier("data/career_training.csv", 5);
    private final NaiveBayesClassifier naiveBayes =
            new NaiveBayesClassifier("data/career_training.csv");
    private final RecommendationService recommender =
            new RecommendationService(careers, classifier, naiveBayes);
    private final SkillGapService skillGap = new SkillGapService();
    private final CertificationAdvisor certs = new CertificationAdvisor("data/certifications.csv");
    private final InternshipAdvisor internships = new InternshipAdvisor("data/internships.csv");
    private final PercentileService percentiles = new PercentileService();
    private final TrendsService trends = new TrendsService();

    private final MentorRepository mentors = new MentorRepository("data/mentors.csv");
    private final MentorshipRepository mentorships = new MentorshipRepository("data/mentorship_requests.csv");
    private final EndorsementRepository endorsements = new EndorsementRepository("data/endorsements.csv");
    private final FacultyRepository faculty = new FacultyRepository("data/faculty.csv");
    private final NotificationRepository notifications = new NotificationRepository("data/notifications.csv");
    private final ApplicationRepository applications = new ApplicationRepository("data/applications.csv");

    // Cross-cutting session/auth helpers, and the per-role page controllers —
    // each depends only on fields declared above it.
    private final AppContext ctx = new AppContext(auth, sessions, notifications);
    private final AuthPages authPages = new AuthPages(auth, mail, sessions, ctx);
    private final StudentPages studentPages = new StudentPages(students, careers, classifier, recommender,
            skillGap, certs, internships, percentiles, applications, endorsements, mentors, mentorships,
            notifications, trends, ctx);
    private final RecruiterPages recruiterPages = new RecruiterPages(students, endorsements, recommender,
            applications, notifications, trends, ctx);
    private final MentorPages mentorPages = new MentorPages(mentors, careers, mentorships, notifications, ctx);
    private final FacultyPages facultyPages = new FacultyPages(faculty, students, endorsements, notifications, ctx);
    private final SharedPages sharedPages = new SharedPages(notifications, students, careers, endorsements,
            recommender, skillGap, applications, trends, ctx);
    private final AdminPages adminPages = new AdminPages(auth, students, applications, ctx);
    private final BenchmarkPages benchmarkPages = new BenchmarkPages(classifier, naiveBayes, ctx);

    public void start(int port) throws IOException {
        auth.ensureAdminAccount();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::route);
        server.start();
        System.out.println("CareerCompass web app running at http://localhost:" + port);
        System.out.println("Mail delivery: " + (mail.isConfigured()
                ? "SMTP configured - real emails will be sent"
                : "DEMO MODE (create data/mail.properties to send real emails)"));
        System.out.println("Roles: Student, Recruiter, Mentor, Faculty, Admin");
        System.out.println("Admin login: admin@careercompass.com / admin123");
    }

    // ------------------------------ routing ------------------------------

    private void route(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            switch (path) {
                case "/style.css" -> css(ex);
                case "/" -> authPages.landingPage(ex);
                case "/login" -> { if (post(method)) authPages.doLogin(ex); else authPages.loginPage(ex, null); }
                case "/register" -> { if (post(method)) authPages.doRegister(ex); else authPages.registerPage(ex, null); }
                case "/verify" -> {
                    if (post(method)) authPages.doVerify(ex);
                    else authPages.verifyPage(ex, q(ex).get("email"), null);
                }
                case "/logout" -> authPages.doLogout(ex);

                case "/student" -> studentPages.dashboard(ex);
                case "/profile" -> { if (post(method)) studentPages.saveProfile(ex); else redirect(ex, "/student"); }
                case "/recommend" -> studentPages.recommendPage(ex);
                case "/career" -> studentPages.careerPage(ex);
                case "/apply" -> { if (post(method)) studentPages.doApply(ex); else redirect(ex, "/student"); }
                case "/applications" -> studentPages.myApplicationsPage(ex);

                case "/mentors" -> studentPages.mentorsPage(ex);
                case "/mentors/request" -> {
                    if (post(method)) studentPages.doMentorshipRequest(ex); else redirect(ex, "/mentors");
                }

                case "/mentor" -> mentorPages.dashboard(ex);
                case "/mentor/setup" -> { if (post(method)) mentorPages.doSetup(ex); else redirect(ex, "/mentor"); }
                case "/mentor/respond" -> { if (post(method)) mentorPages.doRespond(ex); else redirect(ex, "/mentor"); }

                case "/faculty" -> facultyPages.dashboard(ex);
                case "/faculty/setup" -> { if (post(method)) facultyPages.doSetup(ex); else redirect(ex, "/faculty"); }
                case "/faculty/endorse" -> { if (post(method)) facultyPages.doEndorse(ex); else redirect(ex, "/faculty"); }

                case "/recruiter" -> recruiterPages.page(ex);
                case "/shortlist" -> { if (post(method)) recruiterPages.doShortlist(ex); else redirect(ex, "/recruiter"); }
                case "/shortlist/bulk" -> { if (post(method)) recruiterPages.doBulkShortlist(ex); else redirect(ex, "/recruiter"); }
                case "/recruiter/applications" -> recruiterPages.applicationsPage(ex);
                case "/recruiter/applications/update" -> {
                    if (post(method)) recruiterPages.doUpdateApplicationStatus(ex);
                    else redirect(ex, "/recruiter/applications");
                }

                case "/notifications" -> sharedPages.notificationsPage(ex);
                case "/trends" -> sharedPages.trendsPage(ex);
                case "/benchmark" -> benchmarkPages.page(ex);

                case "/admin" -> adminPages.page(ex);
                case "/admin/delete" -> { if (post(method)) adminPages.delete(ex); else redirect(ex, "/admin"); }
                default -> notFound(ex);
            }
        } catch (Exception e) {
            String body = "<div class=\"card\"><h1>Something went wrong</h1><p class=\"sub\">"
                    + esc(e.getMessage()) + "</p><p><a href=\"/\">Back home</a></p></div>";
            html(ex, 500, Pages.shell("Error", "", body));
        }
    }

    private static boolean post(String m) { return "POST".equalsIgnoreCase(m); }

    // Signed-out pages (landing/login/register/verify) live in AuthPages now;
    // see the `authPages` field and its routes above.

    /** Delegates to {@link AppContext}; kept here so the ~100 existing call sites below are unchanged. */
    private String navFor(User u) { return ctx.navFor(u); }

    // ------------------------------ helpers ------------------------------
    // Thin delegates to AppContext/Http, kept under these names so every
    // pre-existing call site below (there are close to a hundred) needed no
    // change. New code should call ctx.*/Http.* directly instead — see
    // AuthPages for the pattern the rest of the controllers will follow.

    private User currentUser(HttpExchange ex) { return ctx.currentUser(ex); }

    private User require(HttpExchange ex, User.Role role) throws IOException {
        return ctx.require(ex, role);
    }

    private String csrfInput(HttpExchange ex) { return ctx.csrfInput(ex); }

    private boolean validCsrf(HttpExchange ex, Map<String, String> f) {
        return ctx.validCsrf(ex, f);
    }

    private static String cookie(HttpExchange ex) { return Http.cookie(ex); }

    private static Map<String, String> q(HttpExchange ex) { return Http.q(ex); }

    private static Map<String, String> form(HttpExchange ex) throws IOException {
        return Http.form(ex);
    }

    private void css(HttpExchange ex) throws IOException {
        byte[] bytes = Pages.CSS.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/css; charset=utf-8");
        // No caching: this stylesheet is still actively changing during
        // development, and a stale cached copy is indistinguishable from a
        // server-side bug from the browser's side — not worth the tradeoff
        // at this stage.
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void html(HttpExchange ex, int status, String page) throws IOException {
        Http.html(ex, status, page);
    }

    private void redirect(HttpExchange ex, String to) throws IOException {
        Http.redirect(ex, to);
    }

    private void notFound(HttpExchange ex) throws IOException {
        html(ex, 404, Pages.shell("Not found", "",
                "<div class=\"card narrow\"><h1>Page not found</h1>"
                        + "<p><a href=\"/\">Back home</a></p></div>"));
    }
}
