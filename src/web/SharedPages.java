package web;

import auth.User;
import com.sun.net.httpserver.HttpExchange;
import model.CareerPath;
import model.Notification;
import model.Student;
import repository.ApplicationRepository;
import repository.EndorsementRepository;
import repository.NotificationRepository;
import repository.StudentRepository;
import service.RecommendationService;
import service.SkillGapService;
import service.TrendsService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static web.Http.redirect;
import static web.Pages.esc;

/**
 * Pages every signed-in role can reach, regardless of which one they are:
 * notifications and platform-wide trends. Extracted out of {@link WebServer}
 * — see {@link AuthPages} for the pattern.
 */
public final class SharedPages {

    private final NotificationRepository notifications;
    private final StudentRepository students;
    private final List<CareerPath> careers;
    private final EndorsementRepository endorsements;
    private final RecommendationService recommender;
    private final SkillGapService skillGap;
    private final ApplicationRepository applications;
    private final TrendsService trends;
    private final AppContext ctx;

    public SharedPages(NotificationRepository notifications, StudentRepository students, List<CareerPath> careers,
            EndorsementRepository endorsements, RecommendationService recommender, SkillGapService skillGap,
            ApplicationRepository applications, TrendsService trends, AppContext ctx) {
        this.notifications = notifications;
        this.students = students;
        this.careers = careers;
        this.endorsements = endorsements;
        this.recommender = recommender;
        this.skillGap = skillGap;
        this.applications = applications;
        this.trends = trends;
        this.ctx = ctx;
    }

    public void notificationsPage(HttpExchange ex) throws IOException {
        User u = ctx.currentUser(ex);
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
        Http.html(ex, 200, Pages.shell("Notifications", ctx.navFor(u), b.toString()));
    }

    public void trendsPage(HttpExchange ex) throws IOException {
        User u = ctx.currentUser(ex);
        if (u == null) { redirect(ex, "/"); return; }

        List<Student> all = students.findAll();
        Map<String, Set<String>> endorsedByStudent = new HashMap<>();
        for (Student s : all) endorsedByStudent.put(s.getEmail(), endorsements.endorsedSkills(s.getEmail()));

        StringBuilder b = new StringBuilder();
        b.append("<h1>Career trends</h1><p class=\"sub\">What CareerCompass's own student population looks like right ")
                .append("now — where interest is concentrated, what's already common, and what's still scarce.</p>");

        if (all.isEmpty()) {
            b.append(Pages.noteBox("No student profiles on file yet — trends will populate as students join and complete their profiles."));
            Http.html(ex, 200, Pages.shell("Trends", ctx.navFor(u), b.toString()));
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

        Http.html(ex, 200, Pages.shell("Trends", ctx.navFor(u), b.toString()));
    }
}
