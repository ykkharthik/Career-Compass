package web;

import auth.AuthService;
import auth.MailService;
import auth.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import exception.InvalidProfileException;
import ml.KnnCareerClassifier;
import model.Application;
import model.CareerPath;
import model.Certification;
import model.CloudEngineering;
import model.Cybersecurity;
import model.DataScience;
import model.Endorsement;
import model.Faculty;
import model.Internship;
import model.Mentor;
import model.MentorshipRequest;
import model.Notification;
import model.ProductManagement;
import model.SoftwareEngineering;
import model.Student;
import model.UiUxDesign;
import repository.ApplicationRepository;
import repository.EndorsementRepository;
import repository.FacultyRepository;
import repository.FileManager;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private final RecommendationService recommender =
            new RecommendationService(careers, classifier);
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

    // Cross-cutting session/auth helpers, and the signed-out (landing/login/
    // register/verify) pages — each depends only on fields declared above.
    private final AppContext ctx = new AppContext(auth, sessions, notifications);
    private final AuthPages authPages = new AuthPages(auth, mail, sessions, ctx);

    private static final String SHORTLIST_PATH = "data/shortlist.csv";

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

                case "/student" -> studentDashboard(ex);
                case "/profile" -> { if (post(method)) saveProfile(ex); else redirect(ex, "/student"); }
                case "/recommend" -> recommendPage(ex);
                case "/career" -> careerPage(ex);
                case "/apply" -> { if (post(method)) doApply(ex); else redirect(ex, "/student"); }
                case "/applications" -> myApplicationsPage(ex);

                case "/mentors" -> mentorsPage(ex);
                case "/mentors/request" -> { if (post(method)) doMentorshipRequest(ex); else redirect(ex, "/mentors"); }

                case "/mentor" -> mentorDashboard(ex);
                case "/mentor/setup" -> { if (post(method)) doMentorSetup(ex); else redirect(ex, "/mentor"); }
                case "/mentor/respond" -> { if (post(method)) doMentorRespond(ex); else redirect(ex, "/mentor"); }

                case "/faculty" -> facultyDashboard(ex);
                case "/faculty/setup" -> { if (post(method)) doFacultySetup(ex); else redirect(ex, "/faculty"); }
                case "/faculty/endorse" -> { if (post(method)) doFacultyEndorse(ex); else redirect(ex, "/faculty"); }

                case "/recruiter" -> recruiterPage(ex);
                case "/shortlist" -> { if (post(method)) doShortlist(ex); else redirect(ex, "/recruiter"); }
                case "/recruiter/applications" -> recruiterApplicationsPage(ex);
                case "/recruiter/applications/update" -> {
                    if (post(method)) doUpdateApplicationStatus(ex); else redirect(ex, "/recruiter/applications");
                }

                case "/notifications" -> notificationsPage(ex);
                case "/trends" -> trendsPage(ex);

                case "/admin" -> adminPage(ex);
                case "/admin/delete" -> { if (post(method)) adminDelete(ex); else redirect(ex, "/admin"); }
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

    // ------------------------------ student ------------------------------

    private void studentDashboard(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.STUDENT);
        if (u == null) return;
        Optional<Student> profile = students.findByEmail(u.getEmail());
        boolean saved = q(ex).containsKey("saved");

        StringBuilder b = new StringBuilder();
        b.append("<h1>Student dashboard</h1><p class=\"sub\">")
                .append(esc(u.getEmail())).append("</p>");
        if (saved) b.append(Pages.noteBox("Profile saved."));

        if (profile.isPresent()) {
            Student s = profile.get();
            Set<String> myEndorsed = endorsements.endorsedSkills(s.getEmail());
            b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Your profile</h2><div class=\"grid2\">");
            b.append("<div><p><b>").append(esc(s.getName())).append("</b> · CGPA ")
                    .append(s.getCgpa()).append("</p><div class=\"chips\">");
            for (String skill : s.getSkills()) {
                boolean isEndorsed = myEndorsed.contains(skill);
                b.append("<span class=\"chip ").append(isEndorsed ? "have\">\u2713 " : "have\">")
                        .append(esc(skill)).append(isEndorsed ? " (verified)" : "").append("</span>");
            }
            b.append("</div><p style=\"margin-top:.8rem\">")
                    .append("<a href=\"/recommend\"><button class=\"brass small\">")
                    .append("See recommendations</button></a></p></div>");
            b.append("<div>").append(SvgCharts.radar(new int[]{
                    s.getInterestCoding(), s.getInterestMath(), s.getInterestDesign(),
                    s.getInterestCommunication(), s.getInterestSecurity()})).append("</div>");
            b.append("</div></div>");
        } else {
            b.append(Pages.noteBox("Start by creating your profile below — recommendations unlock once it's saved."));
        }

        Student s = profile.orElse(null);
        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">")
                .append(profile.isPresent() ? "Update profile" : "Create profile")
                .append("</h2><form method=\"post\" action=\"/profile\">").append(csrfInput(ex))
                .append("<div class=\"grid2\"><div>")
                .append("<label>Full name</label><input name=\"name\" required value=\"")
                .append(s == null ? "" : esc(s.getName())).append("\"></div><div>")
                .append("<label>CGPA (0–10)</label><input name=\"cgpa\" type=\"number\" step=\"0.01\" min=\"0\" max=\"10\" required value=\"")
                .append(s == null ? "" : s.getCgpa()).append("\"></div></div>")
                .append("<label>Skills (comma separated, e.g. java, sql, figma)</label>")
                .append("<input name=\"skills\" value=\"")
                .append(s == null ? "" : esc(String.join(", ", s.getSkills()))).append("\">")
                .append("<h2>Interest ratings (1 = low, 5 = high)</h2><div class=\"grid5\">");
        String[][] interests = {
                {"coding", "Coding", s == null ? "3" : "" + s.getInterestCoding()},
                {"math", "Maths / data", s == null ? "3" : "" + s.getInterestMath()},
                {"design", "Design", s == null ? "3" : "" + s.getInterestDesign()},
                {"comm", "Communication", s == null ? "3" : "" + s.getInterestCommunication()},
                {"security", "Security", s == null ? "3" : "" + s.getInterestSecurity()}};
        for (String[] it : interests) {
            b.append("<div><label>").append(it[1]).append("</label>")
                    .append("<input name=\"").append(it[0])
                    .append("\" type=\"number\" min=\"1\" max=\"5\" required value=\"")
                    .append(it[2]).append("\"></div>");
        }
        b.append("</div><button>Save profile</button></form></div>");

        html(ex, 200, Pages.shell("Dashboard", navFor(u), b.toString()));
    }

    private void saveProfile(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.STUDENT);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!validCsrf(ex, f)) { redirect(ex, "/student"); return; }
        try {
            Set<String> skills = new LinkedHashSet<>(
                    Arrays.asList(f.getOrDefault("skills", "").split(",")));
            Student s = new Student(u.getEmail(), f.get("name"),
                    Double.parseDouble(f.getOrDefault("cgpa", "-1")), skills,
                    Integer.parseInt(f.getOrDefault("coding", "0")),
                    Integer.parseInt(f.getOrDefault("math", "0")),
                    Integer.parseInt(f.getOrDefault("design", "0")),
                    Integer.parseInt(f.getOrDefault("comm", "0")),
                    Integer.parseInt(f.getOrDefault("security", "0")));
            students.save(s);
            redirect(ex, "/student?saved=1");
        } catch (InvalidProfileException | NumberFormatException e) {
            String body = "<div class=\"card narrow\"><h1>Profile not saved</h1>"
                    + Pages.errorBox(e.getMessage() == null ? "Please check the values entered." : e.getMessage())
                    + "<p><a href=\"/student\">Back to dashboard</a></p></div>";
            html(ex, 200, Pages.shell("Profile", navFor(u), body));
        }
    }

    private void recommendPage(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.STUDENT);
        if (u == null) return;
        Optional<Student> profile = students.findByEmail(u.getEmail());
        if (profile.isEmpty()) { redirect(ex, "/student"); return; }
        Student self = profile.get();
        Set<String> endorsed = endorsements.endorsedSkills(self.getEmail());

        var recs = recommender.recommend(self, endorsed);
        StringBuilder b = new StringBuilder();
        b.append("<h1>Career recommendations</h1><p class=\"sub\">Hybrid ranking: ")
                .append("50% transparent rules (skill overlap + interest fit, with faculty-verified skills ")
                .append("weighted higher) and 50% k-NN vote over ")
                .append(classifier.trainingSize()).append(" example profiles.</p>");
        int rank = 0;
        List<Student> allStudents = students.findAll();
        for (var r : recs) {
            rank++;
            int idx = careers.indexOf(r.career);
            int pct = (int) Math.round(r.finalScore * 100);
            b.append("<div class=\"card").append(rank == 1 ? " lead" : "").append("\">")
                    .append("<span class=\"rank\">")
                    .append(rank == 1 ? "PRIMARY BEARING" : String.format("BEARING %02d", rank))
                    .append("</span>")
                    .append("<div class=\"gauge\"><div class=\"head\">")
                    .append("<span class=\"name\">").append(esc(r.career.getName()))
                    .append("</span><span class=\"deg\">").append(pct).append("%</span></div>")
                    .append("<div class=\"track\"><div class=\"fill\" style=\"width:")
                    .append(pct).append("%\"></div>")
                    .append("<div class=\"needle\" style=\"left:").append(pct).append("%\"></div>")
                    .append("</div>")
                    .append("<div class=\"split\">RULES ").append((int) (r.ruleScore * 100))
                    .append("%  &middot;  k-NN ").append((int) (r.knnScore * 100)).append("%</div></div>")
                    .append("<ul class=\"reasons\">");
            for (String reason : r.reasons)
                b.append("<li>").append(esc(reason)).append("</li>");
            var pctile = percentiles.percentileFor(self, r.career, allStudents, recommender);
            if (pctile.available()) {
                b.append("<li>Better fit than ").append(pctile.percentile())
                        .append("% of ").append(pctile.peerCount())
                        .append(" peer profiles for this domain</li>");
            }
            b.append("</ul><div class=\"chips\" style=\"margin-top:.6rem\">");
            for (String role : r.career.getTypicalRoles())
                b.append("<span class=\"chip\">").append(esc(role)).append("</span>");
            b.append("</div><p style=\"margin-top:.7rem\"><a href=\"/career?d=").append(idx)
                    .append("\">Skill gap, certifications &amp; internships →</a></p></div>");
        }
        html(ex, 200, Pages.shell("Recommendations", navFor(u), b.toString()));
    }

    private void careerPage(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.STUDENT);
        if (u == null) return;
        Optional<Student> profile = students.findByEmail(u.getEmail());
        if (profile.isEmpty()) { redirect(ex, "/student"); return; }
        int d;
        try { d = Integer.parseInt(q(ex).getOrDefault("d", "0")); }
        catch (NumberFormatException e) { d = 0; }
        if (d < 0 || d >= careers.size()) d = 0;
        CareerPath career = careers.get(d);
        Student s = profile.get();

        Set<String> have = skillGap.matchedSkills(s, career);
        Set<String> need = skillGap.missingSkills(s, career);

        StringBuilder b = new StringBuilder();
        b.append("<h1>").append(esc(career.getName())).append("</h1><p class=\"sub\">")
                .append(esc(career.getDescription())).append("</p>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Skill gap</h2><div class=\"chips\">");
        for (String sk : have) b.append("<span class=\"chip have\">✓ ").append(esc(sk)).append("</span>");
        for (String sk : need) b.append("<span class=\"chip need\">+ ").append(esc(sk)).append("</span>");
        b.append("</div>");
        if (!need.isEmpty()) {
            b.append("<h2>Development plan</h2><table><tr><th>When</th><th>Learn</th></tr>");
            int month = 1;
            for (String sk : need)
                b.append("<tr><td>Month ").append(month++).append("</td><td>").append(esc(sk))
                        .append(" — one course plus a mini project</td></tr>");
            b.append("</table>");
        } else {
            b.append("<p>Your skills already cover this domain's requirements — you're demo-day ready.</p>");
        }
        b.append("</div>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Certifications</h2>")
                .append("<table><tr><th>Certification</th><th>Provider</th><th>Level</th></tr>");
        for (Certification c : certs.forDomain(career.getName()))
            b.append("<tr><td>").append(esc(c.getName())).append("</td><td>")
                    .append(esc(c.getProvider())).append("</td><td>")
                    .append(esc(c.getLevel())).append("</td></tr>");
        b.append("</table></div>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Internships</h2>")
                .append("<table><tr><th></th><th>Role</th><th>Where</th><th>Duration</th><th>Stipend</th><th>Prerequisites</th><th></th></tr>");
        for (Internship i : internships.forDomain(career.getName())) {
            boolean ready = true;
            for (String pre : i.getPrerequisites().split("\\|"))
                if (!s.getSkills().contains(pre.trim().toLowerCase())) { ready = false; break; }
            b.append("<tr><td><span class=\"badge ").append(ready ? "ready\">READY" : "upskill\">UPSKILL")
                    .append("</span></td>");
            b.append(internshipCells(i));
            boolean applied = applications.alreadyApplied(s.getEmail(), i.getTitle());
            b.append("<td>");
            if (applied) {
                b.append("<span class=\"badge role\">APPLIED</span>");
            } else {
                b.append("<form method=\"post\" action=\"/apply\">").append(csrfInput(ex))
                        .append("<input type=\"hidden\" name=\"domain\" value=\"").append(esc(career.getName())).append("\">")
                        .append("<input type=\"hidden\" name=\"title\" value=\"").append(esc(i.getTitle())).append("\">")
                        .append("<input type=\"hidden\" name=\"d\" value=\"").append(d).append("\">")
                        .append("<button class=\"small brass\">Apply</button></form>");
            }
            b.append("</td></tr>");
        }
        b.append("</table></div><p><a href=\"/recommend\">← Back to recommendations</a></p>");

        html(ex, 200, Pages.shell(career.getName(), navFor(u), b.toString()));
    }

    private String internshipCells(Internship i) {
        String line = i.toString();
        String[] parts = line.split("\\|");
        String title = parts[0].trim();
        String org = parts.length > 1 ? parts[1].trim() : "";
        String dur = parts.length > 2 ? parts[2].trim() : "";
        String stipend = parts.length > 3 ? parts[3].trim() : "";
        String needs = i.getPrerequisites().replace("|", ", ");
        return "<td>" + esc(title) + "</td><td>" + esc(org) + "</td><td>" + esc(dur)
                + "</td><td>" + esc(stipend) + "</td><td>" + esc(needs) + "</td>";
    }

    private void doApply(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.STUDENT);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!validCsrf(ex, f)) { redirect(ex, "/student"); return; }
        String domain = f.getOrDefault("domain", "");
        String title = f.getOrDefault("title", "");
        if (!title.isBlank() && !applications.alreadyApplied(u.getEmail(), title)) {
            String now = Instant.now().toString();
            applications.save(new Application(UUID.randomUUID().toString(), u.getEmail(),
                    domain, title, Application.Status.APPLIED.name(), now, now));
        }
        redirect(ex, "/career?d=" + f.getOrDefault("d", "0"));
    }

    private void myApplicationsPage(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.STUDENT);
        if (u == null) return;
        StringBuilder b = new StringBuilder();
        b.append("<h1>My applications</h1><p class=\"sub\">Every internship you've applied to, and where it stands.</p>");
        b.append("<div class=\"card\"><table><tr><th>Internship</th><th>Domain</th><th>Status</th><th>Applied</th><th>Updated</th></tr>");
        var list = applications.findByStudent(u.getEmail());
        if (list.isEmpty()) b.append("<tr><td colspan=\"5\">No applications yet — apply from a domain's internship list.</td></tr>");
        for (Application a : list) {
            b.append("<tr><td>").append(esc(a.internshipTitle())).append("</td><td>")
                    .append(esc(a.domain())).append("</td><td>").append(statusBadge(a.status()))
                    .append("</td><td>").append(esc(shortDate(a.appliedAt()))).append("</td><td>")
                    .append(esc(shortDate(a.updatedAt()))).append("</td></tr>");
        }
        b.append("</table></div>");
        html(ex, 200, Pages.shell("My Applications", navFor(u), b.toString()));
    }

    private String statusBadge(String status) {
        String cls = switch (status) {
            case "OFFER" -> "ready";
            case "REJECTED" -> "danger-badge";
            case "APPLIED" -> "role";
            default -> "upskill";
        };
        return "<span class=\"badge " + cls + "\">" + esc(status) + "</span>";
    }

    private String shortDate(String iso) {
        try { return iso.substring(0, 10); } catch (Exception e) { return iso; }
    }

    // ------------------------------ mentors (student-facing) ------------------------------

    private void mentorsPage(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.STUDENT);
        if (u == null) return;
        String domainFilter = q(ex).getOrDefault("domain", "");

        StringBuilder b = new StringBuilder();
        b.append("<h1>Mentors</h1><p class=\"sub\">Industry professionals volunteering guidance by domain.</p>");

        b.append("<div class=\"card\"><form method=\"get\" action=\"/mentors\" class=\"filters\">")
                .append("<div><label>Domain</label><select name=\"domain\"><option value=\"\">All domains</option>");
        for (CareerPath c : careers) {
            b.append("<option value=\"").append(esc(c.getName())).append("\"")
                    .append(c.getName().equals(domainFilter) ? " selected" : "").append(">")
                    .append(esc(c.getName())).append("</option>");
        }
        b.append("</select></div><button class=\"small\">Filter</button></form></div>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Available mentors</h2>");
        var mentorList = domainFilter.isBlank() ? mentors.findAll() : mentors.findByDomain(domainFilter);
        if (mentorList.isEmpty()) b.append("<p>No mentors registered for this domain yet.</p>");
        for (Mentor m : mentorList) {
            b.append("<div class=\"card\" style=\"margin-bottom:.7rem\"><b>").append(esc(m.name()))
                    .append("</b> &middot; <span class=\"badge role\">").append(esc(m.domain())).append("</span>")
                    .append(" &middot; ").append(m.yearsExperience()).append(" yrs experience")
                    .append("<p class=\"sub\" style=\"margin:.4rem 0\">").append(esc(m.bio())).append("</p>")
                    .append("<form method=\"post\" action=\"/mentors/request\">").append(csrfInput(ex))
                    .append("<input type=\"hidden\" name=\"mentorEmail\" value=\"").append(esc(m.email())).append("\">")
                    .append("<input type=\"hidden\" name=\"domain\" value=\"").append(esc(m.domain())).append("\">")
                    .append("<label>Message</label><input name=\"message\" placeholder=\"What would you like help with?\" required>")
                    .append("<button class=\"small brass\">Request mentorship</button></form></div>");
        }
        b.append("</div>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">My requests</h2><table>")
                .append("<tr><th>Mentor</th><th>Domain</th><th>Message</th><th>Status</th><th>Reply</th></tr>");
        var mine = mentorships.findByStudent(u.getEmail());
        if (mine.isEmpty()) b.append("<tr><td colspan=\"5\">No requests sent yet.</td></tr>");
        for (MentorshipRequest r : mine) {
            b.append("<tr><td>").append(esc(r.mentorEmail())).append("</td><td>").append(esc(r.domain()))
                    .append("</td><td>").append(esc(r.message())).append("</td><td>")
                    .append(statusBadge(r.status())).append("</td><td>")
                    .append(r.mentorNote() == null || r.mentorNote().isBlank() ? "—" : esc(r.mentorNote()))
                    .append("</td></tr>");
        }
        b.append("</table></div>");

        html(ex, 200, Pages.shell("Mentors", navFor(u), b.toString()));
    }

    private void doMentorshipRequest(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.STUDENT);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!validCsrf(ex, f)) { redirect(ex, "/mentors"); return; }
        String mentorEmail = f.getOrDefault("mentorEmail", "");
        String domain = f.getOrDefault("domain", "");
        String message = f.getOrDefault("message", "");
        if (!mentorEmail.isBlank() && !message.isBlank()) {
            String id = UUID.randomUUID().toString();
            mentorships.save(new MentorshipRequest(id, u.getEmail(), mentorEmail, domain, message,
                    MentorshipRequest.Status.PENDING.name(), "", Instant.now().toString()));
            notifications.add(new Notification(UUID.randomUUID().toString(), mentorEmail,
                    "New mentorship request from " + u.getEmail() + " in " + domain,
                    "/mentor", false, Instant.now().toString()));
        }
        redirect(ex, "/mentors");
    }

    // ------------------------------ mentor role ------------------------------

    private void mentorDashboard(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.MENTOR);
        if (u == null) return;
        Optional<Mentor> mentorProfile = mentors.findByEmail(u.getEmail());

        StringBuilder b = new StringBuilder();
        b.append("<h1>Mentor dashboard</h1><p class=\"sub\">").append(esc(u.getEmail())).append("</p>");

        if (mentorProfile.isEmpty()) {
            b.append(Pages.noteBox("Set up your mentor profile so students can find and request you."));
            b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Mentor profile</h2>")
                    .append("<form method=\"post\" action=\"/mentor/setup\">").append(csrfInput(ex))
                    .append("<label>Display name</label><input name=\"name\" required>")
                    .append("<label>Domain you mentor in</label><select name=\"domain\">");
            for (CareerPath c : careers)
                b.append("<option value=\"").append(esc(c.getName())).append("\">").append(esc(c.getName())).append("</option>");
            b.append("</select><label>Years of experience</label><input name=\"years\" type=\"number\" min=\"0\" max=\"60\" required>")
                    .append("<label>Short bio</label><input name=\"bio\" placeholder=\"What can you help students with?\" required>")
                    .append("<button class=\"brass\">Save mentor profile</button></form></div>");
        } else {
            Mentor m = mentorProfile.get();
            b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Your profile</h2>")
                    .append("<p><b>").append(esc(m.name())).append("</b> &middot; ")
                    .append("<span class=\"badge role\">").append(esc(m.domain())).append("</span> &middot; ")
                    .append(m.yearsExperience()).append(" yrs experience</p><p class=\"sub\">")
                    .append(esc(m.bio())).append("</p></div>");

            var requests = mentorships.findByMentor(u.getEmail());
            b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Requests</h2>");
            var pending = requests.stream().filter(r -> "PENDING".equals(r.status())).toList();
            var resolved = requests.stream().filter(r -> !"PENDING".equals(r.status())).toList();
            if (pending.isEmpty() && resolved.isEmpty()) b.append("<p>No requests yet.</p>");
            for (MentorshipRequest r : pending) {
                b.append("<div class=\"card\" style=\"margin-bottom:.6rem\">")
                        .append("<p><b>").append(esc(r.studentEmail())).append("</b> — ").append(esc(r.domain())).append("</p>")
                        .append("<p class=\"sub\" style=\"margin:.3rem 0\">").append(esc(r.message())).append("</p>")
                        .append("<form method=\"post\" action=\"/mentor/respond\">").append(csrfInput(ex))
                        .append("<input type=\"hidden\" name=\"id\" value=\"").append(esc(r.id())).append("\">")
                        .append("<label>Reply note (optional)</label><input name=\"note\" placeholder=\"A quick pointer or two\">")
                        .append("<button name=\"action\" value=\"accept\" class=\"small brass\" style=\"margin-right:.5rem\">Accept</button>")
                        .append("<button name=\"action\" value=\"decline\" class=\"small danger\">Decline</button>")
                        .append("</form></div>");
            }
            if (!resolved.isEmpty()) {
                b.append("<table><tr><th>Student</th><th>Domain</th><th>Status</th><th>Your reply</th></tr>");
                for (MentorshipRequest r : resolved) {
                    b.append("<tr><td>").append(esc(r.studentEmail())).append("</td><td>").append(esc(r.domain()))
                            .append("</td><td>").append(statusBadge(r.status())).append("</td><td>")
                            .append(r.mentorNote() == null || r.mentorNote().isBlank() ? "—" : esc(r.mentorNote()))
                            .append("</td></tr>");
                }
                b.append("</table>");
            }
            b.append("</div>");
        }
        html(ex, 200, Pages.shell("Mentor", navFor(u), b.toString()));
    }

    private void doMentorSetup(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.MENTOR);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!validCsrf(ex, f)) { redirect(ex, "/mentor"); return; }
        try {
            int years = Integer.parseInt(f.getOrDefault("years", "0"));
            mentors.save(new Mentor(u.getEmail(), f.getOrDefault("name", u.getEmail()),
                    f.getOrDefault("domain", careers.get(0).getName()),
                    f.getOrDefault("bio", ""), Math.max(0, years)));
        } catch (NumberFormatException ignored) { }
        redirect(ex, "/mentor");
    }

    private void doMentorRespond(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.MENTOR);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!validCsrf(ex, f)) { redirect(ex, "/mentor"); return; }
        Optional<MentorshipRequest> found = mentorships.findById(f.getOrDefault("id", ""));
        if (found.isPresent() && found.get().mentorEmail().equalsIgnoreCase(u.getEmail())) {
            MentorshipRequest r = found.get();
            String action = f.getOrDefault("action", "decline");
            String status = "accept".equals(action)
                    ? MentorshipRequest.Status.ACCEPTED.name() : MentorshipRequest.Status.DECLINED.name();
            String note = f.getOrDefault("note", "");
            mentorships.save(r.withResponse(status, note));
            String verb = "accept".equals(action) ? "accepted" : "declined";
            String msg = "Your mentorship request to " + u.getEmail() + " was " + verb
                    + (note.isBlank() ? "." : ": " + note);
            notifications.add(new Notification(UUID.randomUUID().toString(), r.studentEmail(),
                    msg, "/mentors", false, Instant.now().toString()));
        }
        redirect(ex, "/mentor");
    }

    // ------------------------------ faculty ------------------------------

    private void facultyDashboard(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.FACULTY);
        if (u == null) return;
        Optional<Faculty> facultyProfile = faculty.findByEmail(u.getEmail());

        StringBuilder b = new StringBuilder();
        b.append("<h1>Faculty dashboard</h1><p class=\"sub\">").append(esc(u.getEmail())).append("</p>");

        if (facultyProfile.isEmpty()) {
            b.append(Pages.noteBox("Set up your faculty profile to start endorsing student skills."));
            b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Faculty profile</h2>")
                    .append("<form method=\"post\" action=\"/faculty/setup\">").append(csrfInput(ex))
                    .append("<label>Name</label><input name=\"name\" required>")
                    .append("<label>Department</label><input name=\"department\" placeholder=\"e.g. Computer Science\" required>")
                    .append("<button class=\"brass\">Save faculty profile</button></form></div>");
        } else {
            Faculty fac = facultyProfile.get();
            b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Your profile</h2>")
                    .append("<p><b>").append(esc(fac.name())).append("</b> &middot; ").append(esc(fac.department()))
                    .append("</p></div>");

            b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Student roster — endorse verified skills</h2>")
                    .append("<p class=\"sub\">Endorsed skills carry extra weight in a student's recommendation ")
                    .append("score and appear as a verified badge to recruiters.</p>");
            for (Student s : students.findAll()) {
                Set<String> already = endorsements.endorsedSkills(s.getEmail());
                b.append("<div class=\"card\" style=\"margin-bottom:.6rem\"><b>").append(esc(s.getName()))
                        .append("</b> &middot; ").append(esc(s.getEmail())).append(" &middot; CGPA ").append(s.getCgpa())
                        .append("<div class=\"chips\" style=\"margin:.4rem 0\">");
                for (String sk : s.getSkills()) {
                    boolean isEndorsed = already.contains(sk);
                    b.append("<span class=\"chip ").append(isEndorsed ? "have\">\u2713 " : "\">")
                            .append(esc(sk)).append("</span>");
                }
                b.append("</div>");
                if (!s.getSkills().isEmpty()) {
                    b.append("<form method=\"post\" action=\"/faculty/endorse\" style=\"display:flex;gap:.6rem;align-items:flex-end\">")
                            .append(csrfInput(ex))
                            .append("<input type=\"hidden\" name=\"studentEmail\" value=\"").append(esc(s.getEmail())).append("\">")
                            .append("<div style=\"flex:1\"><label style=\"margin-top:0\">Endorse a skill</label><select name=\"skill\">");
                    for (String sk : s.getSkills())
                        b.append("<option value=\"").append(esc(sk)).append("\">").append(esc(sk)).append("</option>");
                    b.append("</select></div><button class=\"small brass\">Endorse</button></form>");
                }
                b.append("</div>");
            }
            b.append("</div>");
        }
        html(ex, 200, Pages.shell("Faculty", navFor(u), b.toString()));
    }

    private void doFacultySetup(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.FACULTY);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!validCsrf(ex, f)) { redirect(ex, "/faculty"); return; }
        faculty.save(new Faculty(u.getEmail(), f.getOrDefault("name", u.getEmail()),
                f.getOrDefault("department", "")));
        redirect(ex, "/faculty");
    }

    private void doFacultyEndorse(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.FACULTY);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!validCsrf(ex, f)) { redirect(ex, "/faculty"); return; }
        String studentEmail = f.getOrDefault("studentEmail", "");
        String skill = f.getOrDefault("skill", "");
        if (!studentEmail.isBlank() && !skill.isBlank()) {
            endorsements.add(new Endorsement(u.getEmail(), studentEmail, skill, Instant.now().toString()));
            String facultyName = faculty.findByEmail(u.getEmail()).map(Faculty::name).orElse(u.getEmail());
            notifications.add(new Notification(UUID.randomUUID().toString(), studentEmail,
                    facultyName + " endorsed your skill: " + skill, "/student", false, Instant.now().toString()));
        }
        redirect(ex, "/faculty");
    }

    // ------------------------------ recruiter ------------------------------

    private void recruiterPage(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.RECRUITER);
        if (u == null) return;
        Map<String, String> query = q(ex);
        String minStr = query.getOrDefault("min", "");
        String skill = query.getOrDefault("skill", "").trim().toLowerCase();
        double min = 0;
        try { if (!minStr.isBlank()) min = Double.parseDouble(minStr); }
        catch (NumberFormatException ignored) { }

        StringBuilder b = new StringBuilder();
        b.append("<h1>Recruiter portal</h1><p class=\"sub\">").append(esc(u.getEmail()))
                .append(" — browse candidates, filter, and shortlist.</p>");
        b.append("<div class=\"card\"><form method=\"get\" action=\"/recruiter\" class=\"filters\">")
                .append("<div><label>Minimum CGPA</label><input name=\"min\" type=\"number\" step=\"0.1\" min=\"0\" max=\"10\" value=\"")
                .append(esc(minStr)).append("\"></div>")
                .append("<div><label>Has skill</label><input name=\"skill\" value=\"")
                .append(esc(skill)).append("\" placeholder=\"e.g. python\"></div>")
                .append("<button class=\"small\">Filter</button></form></div>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Candidates</h2>")
                .append("<table><tr><th>Candidate</th><th>CGPA</th><th>Skills</th><th>Best-fit domain</th><th></th></tr>");
        int shown = 0;
        for (Student s : students.findAll()) {
            if (s.getCgpa() < min) continue;
            if (!skill.isBlank() && !s.getSkills().contains(skill)) continue;
            Set<String> endorsed = endorsements.endorsedSkills(s.getEmail());
            var recs = recommender.recommend(s, endorsed);
            String top = recs.isEmpty() ? "-" : recs.get(0).career.getName();
            b.append("<tr><td><b>").append(esc(s.getName())).append("</b><br><span style=\"color:var(--slate);font-size:.85rem\">")
                    .append(esc(s.getEmail())).append("</span></td><td>").append(s.getCgpa())
                    .append("</td><td>");
            for (String sk : s.getSkills()) {
                b.append("<span class=\"chip ").append(endorsed.contains(sk) ? "have\">\u2713 " : "\">")
                        .append(esc(sk)).append("</span> ");
            }
            b.append("</td><td><span class=\"badge role\">").append(esc(top)).append("</span></td>")
                    .append("<td><form method=\"post\" action=\"/shortlist\">").append(csrfInput(ex))
                    .append("<input type=\"hidden\" name=\"email\" value=\"").append(esc(s.getEmail()))
                    .append("\"><button class=\"small brass\">Shortlist</button></form></td></tr>");
            shown++;
        }
        if (shown == 0)
            b.append("<tr><td colspan=\"5\">No candidates match these filters yet.</td></tr>");
        b.append("</table></div>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">My shortlist</h2><table>")
                .append("<tr><th>Candidate</th><th>Added</th></tr>");
        boolean any = false;
        for (String line : FileManager.readLines(SHORTLIST_PATH)) {
            String[] p = line.split(",", -1);
            if (p.length >= 3 && p[0].equalsIgnoreCase(u.getEmail())) {
                b.append("<tr><td>").append(esc(p[1])).append("</td><td>").append(esc(p[2]))
                        .append("</td></tr>");
                any = true;
            }
        }
        if (!any) b.append("<tr><td colspan=\"2\">Empty — shortlist candidates from the table above.</td></tr>");
        b.append("</table></div>");

        html(ex, 200, Pages.shell("Recruiter", navFor(u), b.toString()));
    }

    private void doShortlist(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.RECRUITER);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!validCsrf(ex, f)) { redirect(ex, "/recruiter"); return; }
        String email = f.getOrDefault("email", "");
        if (students.findByEmail(email).isPresent()) {
            FileManager.appendLine(SHORTLIST_PATH,
                    String.join(",", u.getEmail(), email.toLowerCase(), LocalDate.now().toString()));
            notifications.add(new Notification(UUID.randomUUID().toString(), email.toLowerCase(),
                    "You were shortlisted by a recruiter (" + u.getEmail() + ")", "/student",
                    false, Instant.now().toString()));
        }
        redirect(ex, "/recruiter");
    }

    private void recruiterApplicationsPage(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.RECRUITER);
        if (u == null) return;
        StringBuilder b = new StringBuilder();
        b.append("<h1>Applications pipeline</h1><p class=\"sub\">Every internship application across all candidates.</p>");
        b.append("<div class=\"card\"><table><tr><th>Candidate</th><th>Internship</th><th>Domain</th>")
                .append("<th>Status</th><th>Applied</th><th>Update</th></tr>");
        var list = applications.findAll();
        if (list.isEmpty()) b.append("<tr><td colspan=\"6\">No applications yet.</td></tr>");
        String[] statuses = {"APPLIED", "SHORTLISTED", "INTERVIEW", "OFFER", "REJECTED"};
        for (Application a : list) {
            b.append("<tr><td>").append(esc(a.studentEmail())).append("</td><td>")
                    .append(esc(a.internshipTitle())).append("</td><td>").append(esc(a.domain()))
                    .append("</td><td>").append(statusBadge(a.status())).append("</td><td>")
                    .append(esc(shortDate(a.appliedAt()))).append("</td><td>")
                    .append("<form method=\"post\" action=\"/recruiter/applications/update\" style=\"display:flex;gap:.4rem\">")
                    .append(csrfInput(ex))
                    .append("<input type=\"hidden\" name=\"id\" value=\"").append(esc(a.id())).append("\">")
                    .append("<select name=\"status\">");
            for (String st : statuses)
                b.append("<option value=\"").append(st).append("\"")
                        .append(st.equals(a.status()) ? " selected" : "").append(">").append(st).append("</option>");
            b.append("</select><button class=\"small\">Update</button></form></td></tr>");
        }
        b.append("</table></div>");
        html(ex, 200, Pages.shell("Applications", navFor(u), b.toString()));
    }

    private void doUpdateApplicationStatus(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.RECRUITER);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!validCsrf(ex, f)) { redirect(ex, "/recruiter/applications"); return; }
        Optional<Application> found = applications.findById(f.getOrDefault("id", ""));
        String newStatus = f.getOrDefault("status", "APPLIED");
        if (found.isPresent()) {
            Application a = found.get();
            String now = Instant.now().toString();
            applications.save(a.withStatus(newStatus, now));
            notifications.add(new Notification(UUID.randomUUID().toString(), a.studentEmail(),
                    "Your application for " + a.internshipTitle() + " moved to " + newStatus,
                    "/applications", false, now));
        }
        redirect(ex, "/recruiter/applications");
    }

    // ------------------------------ notifications ------------------------------

    private void notificationsPage(HttpExchange ex) throws IOException {
        User u = currentUser(ex);
        if (u == null) { redirect(ex, "/"); return; }
        var list = notifications.findByRecipient(u.getEmail());
        notifications.markAllRead(u.getEmail());

        StringBuilder b = new StringBuilder();
        b.append("<h1>Notifications</h1><p class=\"sub\">Shortlists, mentorship replies, and skill endorsements, all in one place.</p>");
        b.append("<div class=\"card\">");
        if (list.isEmpty()) {
            b.append("<p>Nothing yet — this fills up as recruiters, mentors, and faculty interact with your profile.</p>");
        } else {
            for (Notification n : list) {
                b.append("<div style=\"padding:.7rem 0;border-bottom:1px solid var(--line)\">")
                        .append(n.read() ? "" : "<b>NEW</b> ")
                        .append(esc(n.message()));
                if (n.link() != null && !n.link().isBlank())
                    b.append(" &middot; <a href=\"").append(esc(n.link())).append("\">View</a>");
                b.append("<div style=\"font-family:var(--mono);font-size:.72rem;color:var(--slate);margin-top:.2rem\">")
                        .append(esc(shortDate(n.createdAt()))).append("</div></div>");
            }
        }
        b.append("</div>");
        html(ex, 200, Pages.shell("Notifications", navFor(u), b.toString()));
    }

    // ------------------------------ trends ------------------------------

    private void trendsPage(HttpExchange ex) throws IOException {
        User u = currentUser(ex);
        if (u == null) { redirect(ex, "/"); return; }

        List<Student> all = students.findAll();
        Map<String, Set<String>> endorsedByStudent = new HashMap<>();
        for (Student s : all) endorsedByStudent.put(s.getEmail(), endorsements.endorsedSkills(s.getEmail()));

        StringBuilder b = new StringBuilder();
        b.append("<h1>Career trends</h1><p class=\"sub\">What CareerCompass's own student population looks like right ")
                .append("now — where interest is concentrated, what's already common, and what's still scarce.</p>");

        if (all.isEmpty()) {
            b.append(Pages.noteBox("No student profiles on file yet — trends will populate as students join and complete their profiles."));
            html(ex, 200, Pages.shell("Trends", navFor(u), b.toString()));
            return;
        }

        b.append("<div class=\"grid2\" style=\"margin-bottom:1.1rem\">")
                .append(stat(all.size(), "Student profiles"))
                .append(stat(applications.findAll().size(), "Internship applications"))
                .append("</div>");

        // ---- domain popularity ----
        var domainCounts = trends.domainFitCounts(all, recommender, endorsedByStudent);
        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Where students are heading</h2>")
                .append("<p class=\"sub\">Each student's #1 recommended domain, tallied across the whole population.</p>");
        for (CareerPath c : careers) {
            int count = domainCounts.getOrDefault(c.getName(), 0);
            int pct = all.isEmpty() ? 0 : (int) Math.round(100.0 * count / all.size());
            b.append("<div class=\"gauge\"><div class=\"head\"><span class=\"name\">").append(esc(c.getName()))
                    .append("</span><span class=\"deg\">").append(count).append(" student").append(count == 1 ? "" : "s")
                    .append("</span></div><div class=\"track\"><div class=\"fill\" style=\"width:").append(pct)
                    .append("%\"></div><div class=\"needle\" style=\"left:").append(pct).append("%\"></div></div></div>");
        }
        b.append("</div>");

        // ---- most common skills vs most common gaps ----
        b.append("<div class=\"grid2\">");
        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Most common skills</h2>")
                .append("<p class=\"sub\">Already well represented on campus.</p><div class=\"chips\">");
        var topSkills = trends.skillFrequency(all, 10);
        if (topSkills.isEmpty()) b.append("<p>No skills on file yet.</p>");
        for (var e : topSkills.entrySet())
            b.append("<span class=\"chip have\">").append(esc(e.getKey())).append(" (").append(e.getValue()).append(")</span>");
        b.append("</div></div>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Most common skill gaps</h2>")
                .append("<p class=\"sub\">Missing most often from students' #1-fit domain — a signal for what to teach next.</p><div class=\"chips\">");
        var topGaps = trends.topSkillGaps(all, recommender, skillGap, endorsedByStudent, 10);
        if (topGaps.isEmpty()) b.append("<p>No gaps detected — every student already covers their top domain.</p>");
        for (var e : topGaps.entrySet())
            b.append("<span class=\"chip need\">").append(esc(e.getKey())).append(" (").append(e.getValue()).append(")</span>");
        b.append("</div></div>");
        b.append("</div>");

        // ---- application funnel ----
        var funnel = trends.applicationFunnel(applications.findAll());
        int funnelTotal = funnel.values().stream().mapToInt(Integer::intValue).sum();
        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Internship pipeline funnel</h2>")
                .append("<p class=\"sub\">Every application on the platform, by current stage.</p>");
        if (funnelTotal == 0) {
            b.append("<p>No applications yet — students can apply from a domain's internship list.</p>");
        } else {
            for (var e : funnel.entrySet()) {
                int pct = (int) Math.round(100.0 * e.getValue() / funnelTotal);
                b.append("<div class=\"gauge\"><div class=\"head\"><span class=\"name\">").append(esc(e.getKey()))
                        .append("</span><span class=\"deg\">").append(e.getValue()).append("</span></div>")
                        .append("<div class=\"track\"><div class=\"fill\" style=\"width:").append(pct)
                        .append("%\"></div><div class=\"needle\" style=\"left:").append(pct).append("%\"></div></div></div>");
            }
        }
        b.append("</div>");

        // ---- average CGPA by domain ----
        var avgCgpa = trends.averageCgpaByTopDomain(all, recommender, endorsedByStudent);
        if (!avgCgpa.isEmpty()) {
            b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Average CGPA by top-fit domain</h2>")
                    .append("<table><tr><th>Domain</th><th>Students</th><th>Average CGPA</th></tr>");
            for (var e : avgCgpa.entrySet()) {
                int count = domainCounts.getOrDefault(e.getKey(), 0);
                b.append("<tr><td>").append(esc(e.getKey())).append("</td><td>").append(count)
                        .append("</td><td>").append(String.format("%.2f", e.getValue())).append("</td></tr>");
            }
            b.append("</table></div>");
        }

        html(ex, 200, Pages.shell("Trends", navFor(u), b.toString()));
    }

    private String stat(Object value, String label) {
        return "<div class=\"stat\"><b>" + value + "</b><span>" + label + "</span></div>";
    }

    // ------------------------------ admin ------------------------------

    private void adminPage(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.ADMIN);
        if (u == null) return;
        List<User> all = auth.allUsers();
        long nStudents = all.stream().filter(x -> x.getRole() == User.Role.STUDENT).count();
        long nRecruiters = all.stream().filter(x -> x.getRole() == User.Role.RECRUITER).count();
        long nMentors = all.stream().filter(x -> x.getRole() == User.Role.MENTOR).count();
        long nFaculty = all.stream().filter(x -> x.getRole() == User.Role.FACULTY).count();
        int nProfiles = students.findAll().size();
        int nApplications = applications.findAll().size();

        StringBuilder b = new StringBuilder();
        b.append("<h1>Admin console</h1><p class=\"sub\">Platform overview and account management.</p>");
        b.append("<div class=\"grid2\" style=\"grid-template-columns:repeat(3,1fr);margin-bottom:1.1rem\">")
                .append(stat(nStudents, "Students")).append(stat(nRecruiters, "Recruiters"))
                .append(stat(nMentors, "Mentors")).append(stat(nFaculty, "Faculty"))
                .append(stat(nProfiles, "Profiles")).append(stat(nApplications, "Applications"))
                .append("</div>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Accounts</h2><table>")
                .append("<tr><th>Email</th><th>Role</th><th>Verified</th><th></th></tr>");
        for (User acc : all) {
            b.append("<tr><td>").append(esc(acc.getEmail())).append("</td><td>")
                    .append(acc.getRole()).append("</td><td>")
                    .append(acc.isVerified() ? "yes" : "no").append("</td><td>");
            if (acc.getRole() != User.Role.ADMIN) {
                b.append("<form method=\"post\" action=\"/admin/delete\">").append(csrfInput(ex))
                        .append("<input type=\"hidden\" name=\"email\" value=\"")
                        .append(esc(acc.getEmail()))
                        .append("\"><button class=\"small danger\">Delete</button></form>");
            }
            b.append("</td></tr>");
        }
        b.append("</table></div>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Student profiles</h2><table>")
                .append("<tr><th>Name</th><th>Email</th><th>CGPA</th><th>Skills</th></tr>");
        for (Student s : students.findAll())
            b.append("<tr><td>").append(esc(s.getName())).append("</td><td>")
                    .append(esc(s.getEmail())).append("</td><td>").append(s.getCgpa())
                    .append("</td><td>").append(esc(String.join(", ", s.getSkills())))
                    .append("</td></tr>");
        b.append("</table></div>");

        html(ex, 200, Pages.shell("Admin", navFor(u), b.toString()));
    }

    private void adminDelete(HttpExchange ex) throws IOException {
        User u = require(ex, User.Role.ADMIN);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!validCsrf(ex, f)) { redirect(ex, "/admin"); return; }
        String email = f.getOrDefault("email", "");
        auth.deleteUser(email);
        students.delete(email);
        redirect(ex, "/admin");
    }

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
