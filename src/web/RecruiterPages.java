package web;

import auth.User;
import com.sun.net.httpserver.HttpExchange;
import model.Application;
import model.Notification;
import model.Student;
import repository.ApplicationRepository;
import repository.EndorsementRepository;
import repository.FileManager;
import repository.NotificationRepository;
import repository.StudentRepository;
import service.RecommendationService;
import service.TrendsService;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
 * Everything a Recruiter sees: candidate search with best-fit ranking, a
 * shortlist, and the cross-candidate internship-application pipeline.
 * Extracted out of {@link WebServer} — see {@link AuthPages} for the pattern.
 */
public final class RecruiterPages {

    private static final String SHORTLIST_PATH = "data/shortlist.csv";

    private final StudentRepository students;
    private final EndorsementRepository endorsements;
    private final RecommendationService recommender;
    private final ApplicationRepository applications;
    private final NotificationRepository notifications;
    private final TrendsService trends;
    private final AppContext ctx;

    public RecruiterPages(StudentRepository students, EndorsementRepository endorsements,
            RecommendationService recommender, ApplicationRepository applications,
            NotificationRepository notifications, TrendsService trends, AppContext ctx) {
        this.students = students;
        this.endorsements = endorsements;
        this.recommender = recommender;
        this.applications = applications;
        this.notifications = notifications;
        this.trends = trends;
        this.ctx = ctx;
    }

    // ------------------------------ candidates ------------------------------

    /** One filtered/ranked row: a candidate plus their already-computed best-fit domain and score. */
    private record Candidate(Student student, Set<String> endorsed, String topDomain, double topScore) {}

    public void page(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.RECRUITER);
        if (u == null) return;
        Map<String, String> query = q(ex);
        String minStr = query.getOrDefault("min", "");
        String skill = query.getOrDefault("skill", "").trim().toLowerCase();
        double min = 0;
        try { if (!minStr.isBlank()) min = Double.parseDouble(minStr); }
        catch (NumberFormatException ignored) { }

        List<Student> all = students.findAll();
        List<Candidate> shown = new ArrayList<>();
        for (Student s : all) {
            if (s.getCgpa() < min) continue;
            if (!skill.isBlank() && !s.getSkills().contains(skill)) continue;
            Set<String> endorsed = endorsements.endorsedSkills(s.getEmail());
            var recs = recommender.recommend(s, endorsed);
            String top = recs.isEmpty() ? "-" : recs.get(0).career.getName();
            double topScore = recs.isEmpty() ? 0 : recs.get(0).finalScore;
            shown.add(new Candidate(s, endorsed, top, topScore));
        }
        // Best-fit first, so a recruiter working down the list sees the
        // strongest matches — for whatever filters they've applied — up top.
        shown.sort(Comparator.comparingDouble(Candidate::topScore).reversed());

        StringBuilder b = new StringBuilder();
        b.append("<h1>Recruiter portal</h1><p class=\"sub\">").append(esc(u.getEmail()))
                .append(" — browse candidates, filter, and shortlist.</p>");

        b.append("<div class=\"grid2\" style=\"grid-template-columns:repeat(3,1fr);margin-bottom:1.1rem\">")
                .append(Pages.stat(all.size(), "Total profiles"))
                .append(Pages.stat(shown.size(), "Shown after filters"))
                .append(Pages.stat(shortlistCountFor(u.getEmail()), "Your shortlist"))
                .append("</div>");

        b.append("<div class=\"card\"><form method=\"get\" action=\"/recruiter\" class=\"filters\">")
                .append("<div><label>Minimum CGPA</label><input name=\"min\" type=\"number\" step=\"0.1\" min=\"0\" max=\"10\" value=\"")
                .append(esc(minStr)).append("\"></div>")
                .append("<div><label>Has skill</label><input name=\"skill\" value=\"")
                .append(esc(skill)).append("\" placeholder=\"e.g. python\"></div>")
                .append("<button class=\"small\">Filter</button></form></div>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Candidates</h2>")
                .append("<p class=\"sub\">Ranked best-fit first, using each candidate's own top recommendation score.</p>")
                .append("<table><tr><th>Candidate</th><th>CGPA</th><th>Skills</th><th>Best-fit domain</th><th></th></tr>");
        if (shown.isEmpty())
            b.append("<tr><td colspan=\"5\">No candidates match these filters yet.</td></tr>");
        for (Candidate c : shown) {
            Student s = c.student();
            b.append("<tr><td><b>").append(esc(s.getName())).append("</b><br><span style=\"color:var(--slate);font-size:.85rem\">")
                    .append(esc(s.getEmail())).append("</span></td><td>").append(s.getCgpa())
                    .append("</td><td>");
            for (String sk : s.getSkills()) {
                b.append("<span class=\"chip ").append(c.endorsed().contains(sk) ? "have\">✓ " : "\">")
                        .append(esc(sk)).append("</span> ");
            }
            b.append("</td><td><span class=\"badge role\">").append(esc(c.topDomain())).append("</span></td>")
                    .append("<td><form method=\"post\" action=\"/shortlist\">").append(ctx.csrfInput(ex))
                    .append("<input type=\"hidden\" name=\"email\" value=\"").append(esc(s.getEmail()))
                    .append("\"><button class=\"small brass\">Shortlist</button></form></td></tr>");
        }
        b.append("</table></div>");

        b.append("<div class=\"card\"><h2 style=\"margin-top:0\">My shortlist</h2><table>")
                .append("<tr><th>Candidate</th><th>Added</th></tr>");
        boolean any = false;
        for (String line : FileManager.readLines(SHORTLIST_PATH)) {
            String[] p = line.split(",", -1);
            if (p.length >= 3 && p[0].equalsIgnoreCase(u.getEmail())) {
                b.append("<tr><td>").append(esc(p[1])).append("</td><td>").append(esc(p[2])).append("</td></tr>");
                any = true;
            }
        }
        if (!any) b.append("<tr><td colspan=\"2\">Empty — shortlist candidates from the table above.</td></tr>");
        b.append("</table></div>");

        Http.html(ex, 200, Pages.shell("Recruiter", ctx.navFor(u), b.toString()));
    }

    private int shortlistCountFor(String recruiterEmail) {
        int count = 0;
        for (String line : FileManager.readLines(SHORTLIST_PATH)) {
            String[] p = line.split(",", -1);
            if (p.length >= 3 && p[0].equalsIgnoreCase(recruiterEmail)) count++;
        }
        return count;
    }

    public void doShortlist(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.RECRUITER);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!ctx.validCsrf(ex, f)) { redirect(ex, "/recruiter"); return; }
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

    // ------------------------------ applications pipeline ------------------------------

    public void applicationsPage(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.RECRUITER);
        if (u == null) return;
        var list = applications.findAll();

        StringBuilder b = new StringBuilder();
        b.append("<h1>Applications pipeline</h1><p class=\"sub\">Every internship application across all candidates.</p>");

        if (!list.isEmpty()) {
            var funnel = trends.applicationFunnel(list);
            int total = funnel.values().stream().mapToInt(Integer::intValue).sum();
            b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Pipeline funnel</h2>");
            for (var e : funnel.entrySet()) {
                int pct = total == 0 ? 0 : (int) Math.round(100.0 * e.getValue() / total);
                b.append(Pages.gauge(e.getKey(), String.valueOf(e.getValue()), pct, null));
            }
            b.append("</div>");
        }

        b.append("<div class=\"card\"><table><tr><th>Candidate</th><th>Internship</th><th>Domain</th>")
                .append("<th>Status</th><th>Applied</th><th>Update</th></tr>");
        if (list.isEmpty()) b.append("<tr><td colspan=\"6\">No applications yet.</td></tr>");
        String[] statuses = {"APPLIED", "SHORTLISTED", "INTERVIEW", "OFFER", "REJECTED"};
        for (Application a : list) {
            b.append("<tr><td>").append(esc(a.studentEmail())).append("</td><td>")
                    .append(esc(a.internshipTitle())).append("</td><td>").append(esc(a.domain()))
                    .append("</td><td>").append(Pages.statusBadge(a.status())).append("</td><td>")
                    .append(esc(Pages.shortDate(a.appliedAt()))).append("</td><td>")
                    .append("<form method=\"post\" action=\"/recruiter/applications/update\" style=\"display:flex;gap:.4rem\">")
                    .append(ctx.csrfInput(ex))
                    .append("<input type=\"hidden\" name=\"id\" value=\"").append(esc(a.id())).append("\">")
                    .append("<select name=\"status\">");
            for (String st : statuses)
                b.append("<option value=\"").append(st).append("\"")
                        .append(st.equals(a.status()) ? " selected" : "").append(">").append(st).append("</option>");
            b.append("</select><button class=\"small\">Update</button></form></td></tr>");
        }
        b.append("</table></div>");
        Http.html(ex, 200, Pages.shell("Applications", ctx.navFor(u), b.toString()));
    }

    public void doUpdateApplicationStatus(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.RECRUITER);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!ctx.validCsrf(ex, f)) { redirect(ex, "/recruiter/applications"); return; }
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
}
