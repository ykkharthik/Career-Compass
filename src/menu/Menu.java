package menu;

import auth.AuthService;
import auth.User;
import exception.InvalidProfileException;
import ml.KnnCareerClassifier;
import ml.NaiveBayesClassifier;
import model.CareerPath;
import model.CloudEngineering;
import model.Cybersecurity;
import model.DataScience;
import model.ProductManagement;
import model.SoftwareEngineering;
import model.Student;
import model.UiUxDesign;
import recruiter.RecruiterPortal;
import repository.StudentRepository;
import service.CertificationAdvisor;
import service.InternshipAdvisor;
import service.RecommendationService;
import service.SkillGapService;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

/**
 * Console UI. Flow: welcome -> register/login (with email OTP verification)
 * -> role-specific menu (student features or recruiter portal).
 */
public class Menu {

    private final Scanner in = new Scanner(System.in);

    private final StudentRepository studentRepo;
    private final AuthService auth;
    private final List<CareerPath> careers = List.of(
            new SoftwareEngineering(), new DataScience(), new Cybersecurity(),
            new CloudEngineering(), new UiUxDesign(), new ProductManagement());

    private final KnnCareerClassifier classifier =
            new KnnCareerClassifier("data/career_training.csv", 5);
    private final NaiveBayesClassifier naiveBayes =
            new NaiveBayesClassifier("data/career_training.csv");
    private final RecommendationService recommender =
            new RecommendationService(careers, classifier, naiveBayes);
    private final SkillGapService skillGap = new SkillGapService();
    private final CertificationAdvisor certAdvisor =
            new CertificationAdvisor("data/certifications.csv");
    private final InternshipAdvisor internshipAdvisor =
            new InternshipAdvisor("data/internships.csv");

    public Menu(StudentRepository studentRepo) {
        this.studentRepo = studentRepo;
        this.auth = new AuthService("data/users.csv");
    }

    public void start() {
        System.out.println("=========================================");
        System.out.println("   CareerCompass - Career Guidance");
        System.out.println("   (k-NN model: " + classifier.trainingSize()
                + " labelled example profiles loaded)");
        System.out.println("=========================================");

        while (true) {
            System.out.println("\n1. Register as Student");
            System.out.println("2. Register as Recruiter");
            System.out.println("3. Log in");
            System.out.println("4. Exit");
            System.out.print("Choose: ");
            switch (in.nextLine().trim()) {
                case "1" -> auth.register(in, User.Role.STUDENT).ifPresent(this::route);
                case "2" -> auth.register(in, User.Role.RECRUITER).ifPresent(this::route);
                case "3" -> auth.login(in).ifPresent(this::route);
                case "4" -> {
                    System.out.println("Goodbye!");
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private void route(User user) {
        if (user.getRole() == User.Role.RECRUITER) {
            new RecruiterPortal(user, studentRepo, recommender, in).start();
        } else {
            studentMenu(user);
        }
    }

    // ------------------------- STUDENT SIDE -------------------------

    private void studentMenu(User user) {
        while (true) {
            System.out.println("\n===== STUDENT MENU (" + user.getEmail() + ") =====");
            System.out.println("1. Create / update my profile");
            System.out.println("2. View my profile");
            System.out.println("3. Get career recommendations");
            System.out.println("4. Skill-gap analysis & development plan");
            System.out.println("5. Certification suggestions");
            System.out.println("6. Internship opportunities");
            System.out.println("7. Delete my profile");
            System.out.println("8. Log out");
            System.out.print("Choose: ");
            switch (in.nextLine().trim()) {
                case "1" -> createOrUpdateProfile(user);
                case "2" -> viewProfile(user);
                case "3" -> recommendations(user);
                case "4" -> gapAnalysis(user);
                case "5" -> certifications(user);
                case "6" -> internships(user);
                case "7" -> {
                    if (studentRepo.delete(user.getEmail()))
                        System.out.println("Profile deleted.");
                    else System.out.println("No profile to delete.");
                }
                case "8" -> { return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private void createOrUpdateProfile(User user) {
        try {
            System.out.print("Full name: ");
            String name = in.nextLine();
            System.out.print("CGPA (0-10): ");
            double cgpa = Double.parseDouble(in.nextLine().trim());
            System.out.print("Your current skills (comma separated, e.g. java,sql,figma): ");
            Set<String> skills = new LinkedHashSet<>(Arrays.asList(in.nextLine().split(",")));

            System.out.println("Rate your interest 1 (low) to 5 (high):");
            int coding = readRating("  Coding / building software: ");
            int math = readRating("  Maths / statistics / data: ");
            int design = readRating("  Design / creativity: ");
            int comm = readRating("  Communication / leading people: ");
            int security = readRating("  Security / how systems break: ");

            Student s = new Student(user.getEmail(), name, cgpa, skills,
                    coding, math, design, comm, security);
            studentRepo.save(s);
            System.out.println("Profile saved.");
        } catch (InvalidProfileException e) {
            System.out.println("Profile rejected: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("Profile rejected: please enter numeric values where asked.");
        }
    }

    private int readRating(String prompt) throws NumberFormatException {
        System.out.print(prompt);
        return Integer.parseInt(in.nextLine().trim());
    }

    private Optional<Student> requireProfile(User user) {
        Optional<Student> s = studentRepo.findByEmail(user.getEmail());
        if (s.isEmpty()) System.out.println("Please create your profile first (option 1).");
        return s;
    }

    private void viewProfile(User user) {
        requireProfile(user).ifPresent(s -> {
            System.out.println("\n" + s);
            System.out.printf("Interests -> coding:%d  math:%d  design:%d  communication:%d  security:%d%n",
                    s.getInterestCoding(), s.getInterestMath(), s.getInterestDesign(),
                    s.getInterestCommunication(), s.getInterestSecurity());
        });
    }

    private void recommendations(User user) {
        requireProfile(user).ifPresent(s -> {
            var recs = recommender.recommend(s);
            System.out.println("\n--- Career Recommendations (hybrid: rules + k-NN + Naive Bayes) ---");
            int rank = 1;
            for (var r : recs) {
                System.out.printf("%d. %-28s  score %.0f%%  (rules %.0f%% | k-NN %.0f%% | Bayes %.0f%%)%n",
                        rank++, r.career.getName(), r.finalScore * 100,
                        r.ruleScore * 100, r.knnScore * 100, r.nbScore * 100);
                for (String reason : r.reasons) System.out.println("      - " + reason);
                System.out.println("      roles: " + String.join(", ", r.career.getTypicalRoles()));
            }
        });
    }

    private Optional<CareerPath> pickCareer() {
        System.out.println("\nPick a career domain:");
        for (int i = 0; i < careers.size(); i++)
            System.out.println("  " + (i + 1) + ". " + careers.get(i).getName());
        System.out.print("Choose: ");
        try {
            int idx = Integer.parseInt(in.nextLine().trim()) - 1;
            if (idx >= 0 && idx < careers.size()) return Optional.of(careers.get(idx));
        } catch (NumberFormatException ignored) { }
        System.out.println("Invalid choice.");
        return Optional.empty();
    }

    private void gapAnalysis(User user) {
        requireProfile(user).ifPresent(s ->
                pickCareer().ifPresent(c -> skillGap.printPlan(s, c)));
    }

    private void certifications(User user) {
        requireProfile(user).ifPresent(s ->
                pickCareer().ifPresent(c -> certAdvisor.print(c.getName())));
    }

    private void internships(User user) {
        requireProfile(user).ifPresent(s ->
                pickCareer().ifPresent(c -> internshipAdvisor.print(c.getName(), s)));
    }
}
