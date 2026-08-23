package web;

import auth.User;
import com.sun.net.httpserver.HttpExchange;
import exception.InvalidProfileException;
import ml.KnnCareerClassifier;
import model.Application;
import model.CareerPath;
import model.Certification;
import model.Internship;
import model.Mentor;
import model.MentorshipRequest;
import model.Notification;
import model.Student;
import repository.ApplicationRepository;
import repository.EndorsementRepository;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static web.Http.form;
import static web.Http.q;
import static web.Http.redirect;
import static web.Pages.esc;

/**
 * Everything a Student sees: profile, career recommendations, per-domain
 * skill gap / certifications / internships, application tracking, and
 * browsing mentors. Extracted out of {@link WebServer} — see {@link AuthPages}
 * for the pattern (a focused class per role, sharing {@link AppContext} for
 * session/access-control concerns).
 */
public final class StudentPages {

    private final StudentRepository students;
    private final List<CareerPath> careers;
    private final KnnCareerClassifier classifier;
    private final RecommendationService recommender;
    private final SkillGapService skillGap;
    private final CertificationAdvisor certs;
    private final InternshipAdvisor internships;
    private final PercentileService percentiles;
    private final ApplicationRepository applications;
    private final EndorsementRepository endorsements;
    private final MentorRepository mentors;
    private final MentorshipRepository mentorships;
    private final NotificationRepository notifications;
    private final TrendsService trends;
    private final AppContext ctx;

    public StudentPages(StudentRepository students, List<CareerPath> careers, KnnCareerClassifier classifier,
            RecommendationService recommender, SkillGapService skillGap, CertificationAdvisor certs,
            InternshipAdvisor internships, PercentileService percentiles, ApplicationRepository applications,
            EndorsementRepository endorsements, MentorRepository mentors, MentorshipRepository mentorships,
            NotificationRepository notifications, TrendsService trends, AppContext ctx) {
        this.students = students;
        this.careers = careers;
        this.classifier = classifier;
        this.recommender = recommender;
        this.skillGap = skillGap;
        this.certs = certs;
        this.internships = internships;
        this.percentiles = percentiles;
        this.applications = applications;
        this.endorsements = endorsements;
        this.mentors = mentors;
        this.mentorships = mentorships;
        this.notifications = notifications;
        this.trends = trends;
        this.ctx = ctx;
    }

    // ------------------------------ dashboard & profile ------------------------------

    public void dashboard(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.STUDENT);
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
                b.append("<span class=\"chip ").append(isEndorsed ? "have\">✓ " : "have\">")
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
                .append("</h2><form method=\"post\" action=\"/profile\">").append(ctx.csrfInput(ex))
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

        Http.html(ex, 200, Pages.shell("Dashboard", ctx.navFor(u), b.toString()));
    }

    public void saveProfile(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.STUDENT);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!ctx.validCsrf(ex, f)) { redirect(ex, "/student"); return; }
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
            Http.html(ex, 200, Pages.shell("Profile", ctx.navFor(u), body));
        }
    }

    // ------------------------------ recommendations ------------------------------

    public void recommendPage(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.STUDENT);
        if (u == null) return;
        Optional<Student> profile = students.findByEmail(u.getEmail());
        if (profile.isEmpty()) { redirect(ex, "/student"); return; }
        Student self = profile.get();
        Set<String> endorsed = endorsements.endorsedSkills(self.getEmail());

        var recs = recommender.recommend(self, endorsed);
        StringBuilder b = new StringBuilder();
        b.append("<h1>Career recommendations</h1><p class=\"sub\">Hybrid ranking: ")
                .append("50% transparent rules (skill overlap + interest fit, with faculty-verified skills ")
                .append("weighted higher), 25% k-NN and 25% Naive Bayes — two different learning paradigms over ")
                .append("the same ").append(classifier.trainingSize())
                .append(" example profiles (see the ML Benchmark page for how they compare).</p>");
        int rank = 0;
        List<Student> allStudents = students.findAll();
        for (var r : recs) {
            rank++;
            int idx = careers.indexOf(r.career);
            int pct = (int) Math.round(r.finalScore * 100);
            String split = "RULES " + (int) (r.ruleScore * 100) + "%  &middot;  k-NN " + (int) (r.knnScore * 100)
                    + "%  &middot;  BAYES " + (int) (r.nbScore * 100) + "%";
            b.append("<div class=\"card").append(rank == 1 ? " lead" : "").append("\">")
                    .append("<span class=\"rank\">")
                    .append(rank == 1 ? "PRIMARY BEARING" : String.format("BEARING %02d", rank))
                    .append("</span>")
                    .append(Pages.gauge(esc(r.career.getName()), pct + "%", pct, split))
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
        Http.html(ex, 200, Pages.shell("Recommendations", ctx.navFor(u), b.toString()));
    }

    public void careerPage(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.STUDENT);
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
                b.append("<form method=\"post\" action=\"/apply\">").append(ctx.csrfInput(ex))
                        .append("<input type=\"hidden\" name=\"domain\" value=\"").append(esc(career.getName())).append("\">")
                        .append("<input type=\"hidden\" name=\"title\" value=\"").append(esc(i.getTitle())).append("\">")
                        .append("<input type=\"hidden\" name=\"d\" value=\"").append(d).append("\">")
                        .append("<button class=\"small brass\">Apply</button></form>");
            }
            b.append("</td></tr>");
        }
        b.append("</table></div><p><a href=\"/recommend\">← Back to recommendations</a></p>");

        Http.html(ex, 200, Pages.shell(career.getName(), ctx.navFor(u), b.toString()));
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

    // ------------------------------ applications ------------------------------

    public void doApply(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.STUDENT);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!ctx.validCsrf(ex, f)) { redirect(ex, "/student"); return; }
        String domain = f.getOrDefault("domain", "");
        String title = f.getOrDefault("title", "");
        if (!title.isBlank() && !applications.alreadyApplied(u.getEmail(), title)) {
            String now = Instant.now().toString();
            applications.save(new Application(UUID.randomUUID().toString(), u.getEmail(),
                    domain, title, Application.Status.APPLIED.name(), now, now));
        }
        redirect(ex, "/career?d=" + f.getOrDefault("d", "0"));
    }

    public void myApplicationsPage(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.STUDENT);
        if (u == null) return;
        var list = applications.findByStudent(u.getEmail());

        StringBuilder b = new StringBuilder();
        b.append("<h1>My applications</h1><p class=\"sub\">Every internship you've applied to, and where it stands.</p>");

        if (!list.isEmpty()) {
            // Same funnel computation TrendsService.applicationFunnel() runs
            // platform-wide (see the Trends page), applied here to just this
            // student's own list.
            var funnel = trends.applicationFunnel(list);
            b.append("<div class=\"grid5\" style=\"margin-bottom:1.1rem\">");
            for (var e : funnel.entrySet())
                b.append(Pages.stat(e.getValue(), e.getKey()));
            b.append("</div>");
        }

        b.append("<div class=\"card\"><table><tr><th>Internship</th><th>Domain</th><th>Status</th><th>Applied</th><th>Updated</th></tr>");
        if (list.isEmpty()) b.append("<tr><td colspan=\"5\">No applications yet — apply from a domain's internship list.</td></tr>");
        for (Application a : list) {
            b.append("<tr><td>").append(esc(a.internshipTitle())).append("</td><td>")
                    .append(esc(a.domain())).append("</td><td>").append(Pages.statusBadge(a.status()))
                    .append("</td><td>").append(esc(Pages.shortDate(a.appliedAt()))).append("</td><td>")
                    .append(esc(Pages.shortDate(a.updatedAt()))).append("</td></tr>");
        }
        b.append("</table></div>");
        Http.html(ex, 200, Pages.shell("My Applications", ctx.navFor(u), b.toString()));
    }

    // ------------------------------ mentors (student-facing) ------------------------------

    public void mentorsPage(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.STUDENT);
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
                    .append("<form method=\"post\" action=\"/mentors/request\">").append(ctx.csrfInput(ex))
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
                    .append(Pages.statusBadge(r.status())).append("</td><td>")
                    .append(r.mentorNote() == null || r.mentorNote().isBlank() ? "—" : esc(r.mentorNote()))
                    .append("</td></tr>");
        }
        b.append("</table></div>");

        Http.html(ex, 200, Pages.shell("Mentors", ctx.navFor(u), b.toString()));
    }

    public void doMentorshipRequest(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.STUDENT);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!ctx.validCsrf(ex, f)) { redirect(ex, "/mentors"); return; }
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
}
