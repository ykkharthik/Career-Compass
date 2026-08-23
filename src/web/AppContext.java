package web;

import auth.AuthService;
import auth.User;
import com.sun.net.httpserver.HttpExchange;
import repository.NotificationRepository;

import java.io.IOException;
import java.util.Map;

import static web.Pages.esc;

/**
 * Cross-cutting session/auth helpers shared by every page controller
 * (auth pages, and each role's dashboard pages): who's signed in, whether
 * they're allowed on this route, where "home" is for their role, the shared
 * top nav, and CSRF token issue/check. Built once in {@link WebServer} and
 * passed by reference to every controller so route-level access control
 * lives in exactly one place instead of being re-implemented per role.
 */
public final class AppContext {

    private final AuthService auth;
    private final SessionManager sessions;
    private final NotificationRepository notifications;

    public AppContext(AuthService auth, SessionManager sessions, NotificationRepository notifications) {
        this.auth = auth;
        this.sessions = sessions;
        this.notifications = notifications;
    }

    public User currentUser(HttpExchange ex) {
        String email = sessions.emailFor(Http.cookie(ex));
        return email == null ? null : auth.findByEmail(email).orElse(null);
    }

    /** Returns the user if signed in with the given role; otherwise redirects and returns null. */
    public User require(HttpExchange ex, User.Role role) throws IOException {
        User u = currentUser(ex);
        if (u == null) { Http.redirect(ex, "/"); return null; }
        if (u.getRole() != role) { Http.redirect(ex, homeFor(u)); return null; }
        return u;
    }

    public String homeFor(User u) {
        return switch (u.getRole()) {
            case STUDENT -> "/student";
            case RECRUITER -> "/recruiter";
            case MENTOR -> "/mentor";
            case FACULTY -> "/faculty";
            case ADMIN -> "/admin";
        };
    }

    /** Unified top-nav per role: role-specific links, then notifications, model insights, sign out. */
    public String navFor(User u) {
        long unread = notifications.unreadCount(u.getEmail());
        String bell = "<a href=\"/notifications\">Notifications" + (unread > 0 ? " (" + unread + ")" : "") + "</a>";
        String base = switch (u.getRole()) {
            case STUDENT -> "<a href=\"/student\">Dashboard</a><a href=\"/recommend\">Recommendations</a>"
                    + "<a href=\"/mentors\">Mentors</a><a href=\"/applications\">My Applications</a>";
            case RECRUITER -> "<a href=\"/recruiter\">Candidates</a><a href=\"/recruiter/applications\">Applications</a>";
            case MENTOR -> "<a href=\"/mentor\">Mentor Dashboard</a>";
            case FACULTY -> "<a href=\"/faculty\">Faculty Dashboard</a>";
            case ADMIN -> "<a href=\"/admin\">Console</a>";
        };
        return base + bell + "<a href=\"/trends\">Trends</a><a href=\"/benchmark\">ML Benchmark</a>"
                + "<a href=\"/logout\">Sign out</a>";
    }

    public String csrfInput(HttpExchange ex) {
        String token = sessions.csrfFor(Http.cookie(ex));
        return "<input type=\"hidden\" name=\"_csrf\" value=\"" + esc(token == null ? "" : token) + "\">";
    }

    public boolean validCsrf(HttpExchange ex, Map<String, String> f) {
        return validCsrfToken(ex, f.get("_csrf"));
    }

    /** Same check as {@link #validCsrf}, for callers that already have the token (e.g. a multi-value form). */
    public boolean validCsrfToken(HttpExchange ex, String token) {
        String expected = sessions.csrfFor(Http.cookie(ex));
        return expected != null && expected.equals(token);
    }
}
