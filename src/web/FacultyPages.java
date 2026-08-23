package web;

import auth.User;
import com.sun.net.httpserver.HttpExchange;
import model.Endorsement;
import model.Faculty;
import model.Notification;
import model.Student;
import repository.EndorsementRepository;
import repository.FacultyRepository;
import repository.NotificationRepository;
import repository.StudentRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static web.Http.form;
import static web.Http.redirect;
import static web.Pages.esc;

/**
 * Everything a Faculty member sees: setting up a profile, and endorsing
 * verified student skills. Extracted out of {@link WebServer} — see
 * {@link AuthPages} for the pattern.
 */
public final class FacultyPages {

    private final FacultyRepository faculty;
    private final StudentRepository students;
    private final EndorsementRepository endorsements;
    private final NotificationRepository notifications;
    private final AppContext ctx;

    public FacultyPages(FacultyRepository faculty, StudentRepository students, EndorsementRepository endorsements,
            NotificationRepository notifications, AppContext ctx) {
        this.faculty = faculty;
        this.students = students;
        this.endorsements = endorsements;
        this.notifications = notifications;
        this.ctx = ctx;
    }

    public void dashboard(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.FACULTY);
        if (u == null) return;
        Optional<Faculty> facultyProfile = faculty.findByEmail(u.getEmail());

        StringBuilder b = new StringBuilder();
        b.append("<h1>Faculty dashboard</h1><p class=\"sub\">").append(esc(u.getEmail())).append("</p>");

        if (facultyProfile.isEmpty()) {
            b.append(Pages.noteBox("Set up your faculty profile to start endorsing student skills."));
            b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Faculty profile</h2>")
                    .append("<form method=\"post\" action=\"/faculty/setup\">").append(ctx.csrfInput(ex))
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
                    b.append("<span class=\"chip ").append(isEndorsed ? "have\">✓ " : "\">")
                            .append(esc(sk)).append("</span>");
                }
                b.append("</div>");
                if (!s.getSkills().isEmpty()) {
                    b.append("<form method=\"post\" action=\"/faculty/endorse\" style=\"display:flex;gap:.6rem;align-items:flex-end\">")
                            .append(ctx.csrfInput(ex))
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
        Http.html(ex, 200, Pages.shell("Faculty", ctx.navFor(u), b.toString()));
    }

    public void doSetup(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.FACULTY);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!ctx.validCsrf(ex, f)) { redirect(ex, "/faculty"); return; }
        faculty.save(new Faculty(u.getEmail(), f.getOrDefault("name", u.getEmail()),
                f.getOrDefault("department", "")));
        redirect(ex, "/faculty");
    }

    public void doEndorse(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.FACULTY);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!ctx.validCsrf(ex, f)) { redirect(ex, "/faculty"); return; }
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
}
