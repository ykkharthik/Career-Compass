package recruiter;

import auth.User;
import model.Student;
import repository.FileManager;
import repository.StudentRepository;
import service.RecommendationService;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Recruiter-side features: browse verified student profiles, filter by CGPA
 * or skill, see each candidate's top recommended domain, and shortlist
 * candidates (persisted to data/shortlist.csv).
 */
public class RecruiterPortal {

    private static final String SHORTLIST_PATH = "data/shortlist.csv";

    private final StudentRepository students;
    private final RecommendationService recommender;
    private final User recruiter;
    private final Scanner in;

    public RecruiterPortal(User recruiter, StudentRepository students,
                           RecommendationService recommender, Scanner in) {
        this.recruiter = recruiter;
        this.students = students;
        this.recommender = recommender;
        this.in = in;
    }

    public void start() {
        while (true) {
            System.out.println("\n===== RECRUITER PORTAL (" + recruiter.getEmail() + ") =====");
            System.out.println("1. View all candidate profiles");
            System.out.println("2. Filter by minimum CGPA");
            System.out.println("3. Filter by skill");
            System.out.println("4. Shortlist a candidate");
            System.out.println("5. View my shortlist");
            System.out.println("6. Log out");
            System.out.print("Choose: ");
            switch (in.nextLine().trim()) {
                case "1" -> show(students.findAll());
                case "2" -> filterByCgpa();
                case "3" -> filterBySkill();
                case "4" -> shortlist();
                case "5" -> viewShortlist();
                case "6" -> { return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private void show(List<Student> list) {
        if (list.isEmpty()) {
            System.out.println("No candidate profiles found.");
            return;
        }
        System.out.println();
        for (Student s : list) {
            var recs = recommender.recommend(s);
            String topDomain = recs.isEmpty() ? "-" : recs.get(0).career.getName();
            System.out.printf("  %-28s | %s%n      best-fit domain: %s%n",
                    s.getEmail(), s, topDomain);
        }
    }

    private void filterByCgpa() {
        System.out.print("Minimum CGPA: ");
        try {
            double min = Double.parseDouble(in.nextLine().trim());
            show(students.findAll().stream()
                    .filter(s -> s.getCgpa() >= min)
                    .collect(Collectors.toList()));
        } catch (NumberFormatException e) {
            System.out.println("Please enter a number.");
        }
    }

    private void filterBySkill() {
        System.out.print("Skill to search for: ");
        String skill = in.nextLine().trim().toLowerCase(Locale.ROOT);
        show(students.findAll().stream()
                .filter(s -> s.getSkills().contains(skill))
                .collect(Collectors.toList()));
    }

    private void shortlist() {
        System.out.print("Candidate email to shortlist: ");
        String email = in.nextLine().trim().toLowerCase(Locale.ROOT);
        if (students.findByEmail(email).isEmpty()) {
            System.out.println("No candidate with that email.");
            return;
        }
        FileManager.appendLine(SHORTLIST_PATH,
                String.join(",", recruiter.getEmail(), email, LocalDate.now().toString()));
        System.out.println("Shortlisted " + email + ".");
    }

    private void viewShortlist() {
        System.out.println("\n--- My Shortlist ---");
        boolean any = false;
        for (String line : FileManager.readLines(SHORTLIST_PATH)) {
            String[] p = line.split(",", -1);
            if (p.length >= 3 && p[0].equalsIgnoreCase(recruiter.getEmail())) {
                System.out.println("  " + p[1] + "  (added " + p[2] + ")");
                any = true;
            }
        }
        if (!any) System.out.println("  (empty)");
    }
}
