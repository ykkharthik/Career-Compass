package web;

import auth.AuthService;
import auth.User;
import com.sun.net.httpserver.HttpExchange;
import model.Student;
import repository.ApplicationRepository;
import repository.StudentRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static web.Http.form;
import static web.Http.redirect;
import static web.Pages.esc;

/**
 * Everything an Admin sees: platform-wide account and profile management.
 * Extracted out of {@link WebServer} — see {@link AuthPages} for the pattern.
 */
public final class AdminPages {

    private final AuthService auth;
    private final StudentRepository students;
    private final ApplicationRepository applications;
    private final AppContext ctx;

    public AdminPages(AuthService auth, StudentRepository students, ApplicationRepository applications, AppContext ctx) {
        this.auth = auth;
        this.students = students;
        this.applications = applications;
        this.ctx = ctx;
    }

    public void page(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.ADMIN);
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
                b.append("<form method=\"post\" action=\"/admin/delete\">").append(ctx.csrfInput(ex))
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

        Http.html(ex, 200, Pages.shell("Admin", ctx.navFor(u), b.toString()));
    }

    public void delete(HttpExchange ex) throws IOException {
        User u = ctx.require(ex, User.Role.ADMIN);
        if (u == null) return;
        Map<String, String> f = form(ex);
        if (!ctx.validCsrf(ex, f)) { redirect(ex, "/admin"); return; }
        String email = f.getOrDefault("email", "");
        auth.deleteUser(email);
        students.delete(email);
        redirect(ex, "/admin");
    }
}
