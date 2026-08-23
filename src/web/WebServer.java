package web;

import auth.AuthService;
import auth.MailService;
import auth.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ml.KnnCareerClassifier;
import model.Application;
import model.CareerPath;
import model.CloudEngineering;
import model.Cybersecurity;
import model.DataScience;
import model.Endorsement;
import model.Faculty;
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
import java.util.HashMap;
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

    // Cross-cutting session/auth helpers, and the per-role page controllers —
    // each depends only on fields declared above it.
    private final AppContext ctx = new AppContext(auth, sessions, notifications);
    private final AuthPages authPages = new AuthPages(auth, mail, sessions, ctx);
    private final StudentPages studentPages = new StudentPages(students, careers, classifier, recommender,
            skillGap, certs, internships, percentiles, applications, endorsements, mentors, mentorships,
            notifications, trends, ctx);
    private final RecruiterPages recruiterPages = new RecruiterPages(students, endorsements, recommender,
            applications, notifications, trends, ctx);

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

                case "/mentor" -> mentorDashboard(ex);
                case "/mentor/setup" -> { if (post(method)) doMentorSetup(ex); else redirect(ex, "/mentor"); }
                case "/mentor/respond" -> { if (post(method)) doMentorRespond(ex); else redirect(ex, "/mentor"); }

                case "/faculty" -> facultyDashboard(ex);
                case "/faculty/setup" -> { if (post(method)) doFacultySetup(ex); else redirect(ex, "/faculty"); }
                case "/faculty/endorse" -> { if (post(method)) doFacultyEndorse(ex); else redirect(ex, "/faculty"); }

                case "/recruiter" -> recruiterPages.page(ex);
                case "/shortlist" -> { if (post(method)) recruiterPages.doShortlist(ex); else redirect(ex, "/recruiter"); }
                case "/recruiter/applications" -> recruiterPages.applicationsPage(ex);
                case "/recruiter/applications/update" -> {
                    if (post(method)) recruiterPages.doUpdateApplicationStatus(ex);
                    else redirect(ex, "/recruiter/applications");
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
                            .append("</td><td>").append(Pages.statusBadge(r.status())).append("</td><td>")
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
                        .append(esc(Pages.shortDate(n.createdAt()))).append("</div></div>");
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
                .append(Pages.stat(all.size(), "Student profiles"))
                .append(Pages.stat(applications.findAll().size(), "Internship applications"))
                .append("</div>");

        // ---- domain popularity ----
        var domainCounts = trends.domainFitCounts(all, recommender, endorsedByStudent);
        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Where students are heading</h2>")
                .append("<p class=\"sub\">Each student's #1 recommended domain, tallied across the whole population.</p>");
        for (CareerPath c : careers) {
            int count = domainCounts.getOrDefault(c.getName(), 0);
            int pct = all.isEmpty() ? 0 : (int) Math.round(100.0 * count / all.size());
            b.append(Pages.gauge(esc(c.getName()), count + " student" + (count == 1 ? "" : "s"), pct, null));
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
                b.append(Pages.gauge(esc(e.getKey()), String.valueOf(e.getValue()), pct, null));
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
                .append(Pages.stat(nStudents, "Students")).append(Pages.stat(nRecruiters, "Recruiters"))
                .append(Pages.stat(nMentors, "Mentors")).append(Pages.stat(nFaculty, "Faculty"))
                .append(Pages.stat(nProfiles, "Profiles")).append(Pages.stat(nApplications, "Applications"))
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
