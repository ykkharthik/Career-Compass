package web;

import auth.User;
import com.sun.net.httpserver.HttpExchange;
import model.CareerPath;
import model.Mentor;
import model.MentorshipRequest;
import model.Notification;
import repository.MentorRepository;
import repository.MentorshipRepository;
import repository.NotificationRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static web.Http.form;
import static web.Http.redirect;
import static web.Pages.esc;

/**
 * Everything a Mentor sees: setting up a profile, and accepting/declining
 * student mentorship requests. Extracted out of {@link WebServer} — see
 * {@link AuthPages} for the pattern.
 */
public final class MentorPages {

    private final MentorRepository mentors;
    private final List<CareerPath> careers;
    private final MentorshipRepository mentorships;
    private final NotificationRepository notifications;
    private final AppContext ctx;

    public MentorPages(MentorRepository mentors, List<CareerPath> careers, MentorshipRepository mentorships,
            NotificationRepository notifications, AppContext ctx) {
        this.mentors = mentors;
        this.careers = careers;
        this.mentorships = mentorships;
        this.notifications = notifications;
        this.ctx = ctx;
    }

    public void dashboard(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.MENTOR);
        if (u == null) return;
        Optional<Mentor> mentorProfile = mentors.findByEmail(u.getEmail());

        StringBuilder b = new StringBuilder();
        b.append("<h1>Mentor dashboard</h1><p class=\"sub\">").append(esc(u.getEmail())).append("</p>");

        if (mentorProfile.isEmpty()) {
            b.append(Pages.noteBox("Set up your mentor profile so students can find and request you."));
            b.append("<div class=\"card\"><h2 style=\"margin-top:0\">Mentor profile</h2>")
                    .append("<form method=\"post\" action=\"/mentor/setup\">").append(ctx.csrfInput(ex))
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
                        .append("<form method=\"post\" action=\"/mentor/respond\">").append(ctx.csrfInput(ex))
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
        Http.html(ex, 200, Pages.shell("Mentor", ctx.navFor(u), b.toString()));
    }

    public void doSetup(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.MENTOR);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!ctx.validCsrf(ex, f)) { redirect(ex, "/mentor"); return; }
        try {
            int years = Integer.parseInt(f.getOrDefault("years", "0"));
            mentors.save(new Mentor(u.getEmail(), f.getOrDefault("name", u.getEmail()),
                    f.getOrDefault("domain", careers.get(0).getName()),
                    f.getOrDefault("bio", ""), Math.max(0, years)));
        } catch (NumberFormatException ignored) { }
        redirect(ex, "/mentor");
    }

    public void doRespond(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.MENTOR);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!ctx.validCsrf(ex, f)) { redirect(ex, "/mentor"); return; }
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
}
