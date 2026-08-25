# CareerCompass – Career Guidance Platform

CS5304 Java Programming project (Chennai Institute of Technology).
A five-role web application (and still a console app), built on **JDK 21**.
The HTTP server, the SMTP mail client, and every piece of the machine-learning
pipeline are written in plain Java with zero external libraries. Two
deliberate exceptions to that, each called out where it matters below:
**Bootstrap 5** (frontend polish, loaded from its CDN) and **H2** (one
entity — notifications — persisted through a real database via JDBC instead
of a CSV file, to demonstrate JDBC/DAO properly rather than just in prose).

## Run the web app

The H2 driver jar (`lib/h2-*.jar`) needs to be on the classpath alongside
your own compiled classes — everything else is still plain Java.

```
javac -cp "lib/h2-2.2.224.jar" -d out $(find src -name "*.java")
java -cp "out:lib/h2-2.2.224.jar" web.WebMain
```

Then open **http://localhost:8080**. Use `java -cp "out:lib/h2-2.2.224.jar" web.WebMain 9090`
for a different port. Run from the project root so `data/` (and the H2
database file it creates there) is found.

Windows (PowerShell), if `find` is unavailable — note the `;` classpath
separator instead of `:`:
```
javac -cp "lib\h2-2.2.224.jar" -d out (Get-ChildItem -Recurse -Filter *.java src | % FullName)
java -cp "out;lib\h2-2.2.224.jar" web.WebMain
```

The console version still works, and doesn't need H2 on its classpath since
it never touches notifications: `java -cp out Main`

## Run the tests

```
java -cp "out:lib/h2-2.2.224.jar" test.TestRunner
```

(`out;lib\h2-2.2.224.jar` on Windows.) A from-scratch test runner — no
JUnit, matching the rest of the project. It uses reflection to find every
`test*` method across seven test classes covering both ML classifiers, the
k-d tree's correctness (the same verification the ML component and
`/benchmark` describe, made permanent so it can never silently regress),
the recommendation blend, skill-gap analysis, the JDBC-backed notification
repository, and auth (registration, login, lockout, password hashing) —
every test that touches persistence uses its own scratch file, never the
real `data/` files.

## Demo accounts (password for all: `demo123`, admin: `admin123`)

| Email | Role |
|---|---|
| priya@cit.edu.in | Student — Data-Science-leaning profile, 2 faculty-verified skills |
| arjun@cit.edu.in | Student — Cybersecurity-leaning profile |
| meena@cit.edu.in | Student — UI/UX-leaning profile |
| rahul@cit.edu.in | Student — Cloud/DevOps-leaning profile |
| ananya@cit.edu.in | Student — Product-Management-leaning profile |
| recruiter@demo.com | Recruiter |
| mentor@demo.com | Mentor — Ravi Chandran, Data Science, 10 yrs experience |
| swe.mentor@demo.com | Mentor — Ananth Rao, Software Engineering |
| security.mentor@demo.com | Mentor — Divya Menon, Cybersecurity |
| cloud.mentor@demo.com | Mentor — Karthik Iyer, Cloud & DevOps Engineering |
| design.mentor@demo.com | Mentor — Priyanka Shah, UI/UX Design |
| pm.mentor@demo.com | Mentor — Rohan Verma, Product Management |
| faculty@demo.com | Faculty — Dr. S. Kumar, Computer Science dept |
| admin@careercompass.com | Admin (`admin123`) |

## Roles

**Student** — profile (CGPA, skills, five interest ratings, shown as a radar
chart); hybrid career recommendations across 6 domains with a full score
breakdown and a peer-percentile benchmark; skill-gap analysis with a
month-by-month plan; certification suggestions; internship listings with a
one-click Apply and a live application-status tracker.

**Recruiter** — browse candidate profiles with each one's best-fit domain and
faculty-verified skills flagged; filter by CGPA or skill; shortlist
candidates; manage the internship-application pipeline (Applied → Shortlisted
→ Interview → Offer/Rejected) across every candidate.

**Mentor** — industry professionals set up a domain + bio + experience
profile; students browse and request mentorship; mentors accept or decline
with a reply note, which the student sees as a notification.

**Faculty** — academic advisors browse the student roster and endorse
specific skills. An endorsed skill isn't just a badge: it's weighted 1.3×
higher than a self-reported skill in the recommendation engine's rule-based
score, and shown to recruiters as a verified signal.

**Admin** — platform-wide account and profile management.

Every cross-role action (shortlisted, mentor replied, skill endorsed,
application status changed) creates an in-app notification for the student —
closing the loop across all five roles rather than leaving each one siloed.

**Trends** — a platform-wide analytics page, visible to every role: which
domains students are gravitating toward, the most common skills on file vs.
the most common gaps (what to teach next), the internship pipeline funnel by
stage, and average CGPA by top-fit domain. Computed live from
`service/TrendsService` on top of the same recommendation and skill-gap logic
the rest of the app uses — not a separate reporting pipeline.

## Email verification

Out of the box the app runs in **demo mode**: the 6-digit code appears on the
verification page instead of being emailed, so the demo works with no setup
and no internet.

To send **real emails**, copy `data/mail.properties.example` to
`data/mail.properties` and fill in SMTP details (Gmail needs an **App
Password** from myaccount.google.com/apppasswords, not your normal password).
`auth/SmtpMailSender` implements the SMTP protocol directly over an SSL
socket — `EHLO → AUTH LOGIN → MAIL FROM → RCPT TO → DATA → QUIT`, including
RFC 5321 dot-stuffing — no mail library required. If a send fails for any
reason the app automatically falls back to demo mode.

## Accounts and security

- One account per email id, format validation, duplicate rejection
- OTP email verification (3 attempts) before an account activates
- Passwords stored as salted SHA-256 hashes, never in plain text
- **Brute-force lockout**: 5 failed logins locks the account for 5 minutes
- **Session expiry**: sessions idle out after 30 minutes, and a daemon
  background thread (`SessionManager`'s constructor) sweeps every 5 minutes
  for sessions that went idle but were never touched again to trigger lazy
  eviction — otherwise an abandoned session (a closed tab, no explicit
  logout) would sit in memory forever
- **CSRF protection**: every authenticated state-changing form carries a
  per-session token, checked on submit
- Role-based access control enforced on every route, not just hidden nav links

## Persistence: CSV, and one entity via real JDBC

Every entity is CSV-backed except one: `NotificationRepository` persists to
an embedded **H2** database over **JDBC**, implementing a generic `Dao<T>`
interface (`findAll`/`findById`/`save`) that any storage backend could sit
behind. Converting all seven repositories to a database was judged not
worth the risk this close to a deadline; one clean, real conversion
demonstrates the same JDBC + DAO pattern properly, with prepared statements
throughout (never string-concatenated SQL) and its own test coverage
(`test/NotificationRepositoryTest`), rather than spreading that risk across
every entity in the app. See **Run the web app** above for the one extra
step this adds: the H2 driver jar needs to be on the classpath.

## The ML component — pure Java, no libraries

Two independent learning paradigms sit alongside the transparent rule engine,
each implemented from scratch against the same 43 labelled example profiles
in `data/career_training.csv` (5 interest ratings + scaled CGPA → career
domain):

1. **`ml/KnnCareerClassifier`** — instance-based learning. A student's
   feature vector is compared to every example by **Euclidean distance**;
   the nearest k examples vote on the domain.
2. **`ml/NaiveBayesClassifier`** — generative learning. Fits a Gaussian
   distribution per feature per domain from the training data, then picks
   the domain whose fitted distributions best explain the new profile
   (Bayes' rule, assuming feature independence given the class).

The final ranking is 50% the rule-based score (skill overlap, weighted
higher for faculty-endorsed skills, + interest alignment) and 50% split
evenly between the two learners — two different models agreeing on a domain
is stronger evidence than either alone. Every recommendation shows all three
score components and the reasons behind them, so the ML adds a learning
signal without becoming a black box.

**`ml/KdTree`** is a from-scratch k-d tree that answers the same k-NN
nearest-neighbour queries in expected O(log n) instead of brute force's
O(n). It uses a deterministic secondary sort key so that ties resolve
identically to the brute-force scan. `kdNearestNeighbours()` is available on
the classifier for any caller that needs the faster lookup as the training
set scales past a few dozen rows; the shipped predictions currently use the
brute-force path since correctness there needs no argument at all.

**The `/benchmark` page** (reachable directly at that URL once signed in —
deliberately left out of the nav, since it's a developer diagnostics page
rather than something a student or recruiter needs) **re-runs this
verification live, on every request**, rather than only asserting it in
prose: it checks the k-d tree against brute force on all 43 training
points as queries, times both lookup strategies, and reports leave-one-out
cross-validation accuracy for k-NN vs Naive Bayes. Two real bugs were
caught this way during development, both
worth mentioning in viva if asked how correctness was established rather
than just asserted: a sign error in the tie-break comparator that initially
inverted the eviction condition, and — found only after the benchmark page
started comparing exact neighbour lists instead of just majority-vote
outcomes — the k-d tree's final result list was sorted by distance alone,
so two neighbours exactly tied at the k-th boundary could come out in
whatever order the priority queue's internal heap happened to yield instead
of the same deterministic order brute force uses. The set of k neighbours
chosen was always correct (so votes/predictions were never affected), but
the *order* silently drifted from brute force's — a good example of how an
order-insensitive check (majority vote) can hide a real, narrower
correctness gap that a stricter one (exact list comparison) catches.

## Interface design

Hand-written HTML and CSS, no React and no build step, themed as a
navigation chart because the engine literally computes a bearing and a
distance: chart-paper graticule, deep ink panel, magenta for markers (the
colour real charts reserve for aids to navigation), monospaced readouts for
every number. The signature element is the **bearing tape** on the
recommendations page — a graduated scale with a needle at each domain's
score. A hand-built SVG radar chart visualizes each student's five interest
dimensions with no charting library.

**Bootstrap 5** is loaded from its CDN (`web/Pages.BOOTSTRAP_CSS/BOOTSTRAP_JS`
— the same pattern already used for Google Fonts) as the one deliberate
exception to the zero-external-libraries story above: it supplies the
navbar's mobile collapse behaviour and the base component/utility layer,
while a small progressive-enhancement script retrofits Bootstrap's
`.btn`/`.form-control`/`.table` classes onto the existing hand-written
markup by selector — so every page picks up Bootstrap's component
plumbing without hand-editing each of the ~9 page-builder classes — and
`style.css` loads after Bootstrap so the existing brand theme (ink panel,
magenta/teal/gold accents, monospace type) wins the cascade rather than
being replaced by Bootstrap's default look.

## Architecture

`WebServer` is a thin router — every role's pages live in their own class,
each depending only on the repositories/services it actually needs, and
sharing session/access-control logic through `AppContext` rather than
duplicating it:

```mermaid
graph TD
    WM[WebMain] --> WS[WebServer<br/><i>router</i>]
    WS --> AP[AuthPages<br/><i>landing/login/register/verify</i>]
    WS --> SP[StudentPages]
    WS --> RP[RecruiterPages]
    WS --> MP[MentorPages]
    WS --> FP[FacultyPages]
    WS --> ShP[SharedPages<br/><i>notifications/trends</i>]
    WS --> AdP[AdminPages]
    WS --> BP[BenchmarkPages<br/><i>dev-only, unlinked</i>]

    AP & SP & RP & MP & FP & ShP & AdP --> CTX[AppContext<br/><i>session · CSRF · nav</i>]
    SP & RP --> REC[RecommendationService]
    REC --> RULE[rule engine<br/>50%]
    REC --> KNN[KnnCareerClassifier<br/>25%]
    REC --> NB[NaiveBayesClassifier<br/>25%]
    KNN --> KDT[KdTree<br/><i>O(log n) lookup</i>]

    SP & RP & MP & FP & AdP --> REPO[(repository/*<br/>CSV-backed)]
    ShP --> NOTIF[(NotificationRepository<br/><i>JDBC + H2</i>)]
    CTX --> AUTH[AuthService]
```

```
src/
├── Main.java                  console entry point
├── web/
│   ├── WebMain.java            web entry point  → http://localhost:8080
│   ├── WebServer.java          router only (com.sun.net.httpserver) + shared HTTP plumbing
│   ├── AppContext.java         session/CSRF/nav — shared by every page class below
│   ├── Http.java               stateless request/response helpers
│   ├── AuthPages.java          landing, login, register, verify
│   ├── StudentPages.java       dashboard, recommendations, applications, mentors
│   ├── RecruiterPages.java     candidate search, shortlist, application pipeline
│   ├── MentorPages.java        mentor profile + request accept/decline
│   ├── FacultyPages.java       faculty profile + skill endorsement
│   ├── SharedPages.java        notifications, trends (any signed-in role)
│   ├── AdminPages.java         account/profile management
│   ├── BenchmarkPages.java     live ML verification (dev-only, not in nav)
│   ├── SessionManager.java     cookie sessions, CSRF tokens, idle expiry
│   ├── SvgCharts.java          hand-built interest radar chart
│   └── Pages.java              CSS + page shell + shared HTML helpers
├── auth/                       User, AuthService (register/login/lockout),
│                               EmailVerifier, MailService, SmtpMailSender
├── model/                      Student, abstract CareerPath + 6 subclasses,
│                               Certification, Internship, Mentor,
│                               MentorshipRequest, Endorsement, Faculty,
│                               Notification, Application
├── repository/                 FileManager (CSV I/O), Dao<T> (generic DAO
│                               interface), one repository per entity — all
│                               CRUD over CSV except NotificationRepository,
│                               which implements Dao<Notification> via JDBC/H2
├── ml/                         KnnCareerClassifier, NaiveBayesClassifier, KdTree
├── service/                    RecommendationService (rules + k-NN + Naive
│                               Bayes blend), SkillGapService,
│                               CertificationAdvisor, InternshipAdvisor,
│                               PercentileService, TrendsService
├── util/                       Rankings — generic, bounded-type ranking
│                               helpers shared by TrendsService and NaiveBayesClassifier
├── recruiter/                  RecruiterPortal (console recruiter view)
├── menu/                       Menu (console UI)
├── test/                       TestRunner + Assert (from-scratch test suite)
└── exception/                  InvalidProfileException
lib/
└── h2-2.2.224.jar              H2's JDBC driver — the one external jar this
                                project depends on; a single pure-Java file,
                                no native libraries
data/
├── career_training.csv        43 labelled profiles for k-NN / Naive Bayes
├── certifications.csv         28 certifications across 6 domains
├── internships.csv            20 internship listings with prerequisites
├── users.csv / students.csv   accounts and profiles
├── mentors.csv / mentorship_requests.csv
├── faculty.csv / endorsements.csv
├── applications.csv           applications, still CSV-backed
├── notifications.mv.db        the H2 database file (created on first run)
└── mail.properties.example    template for real email sending
```

## Java concepts demonstrated

**OOP fundamentals** — inheritance and polymorphism (the `CareerPath`
hierarchy), abstract classes and abstract methods, encapsulation, static
members, `final` methods and classes (e.g. `CareerPath.toString()` is
`final` — every subclass supplies a name through the abstract `getName()`
hook instead), a `protected` static member (`CareerPath.skills()`),
constructors, access specifiers.

**Interfaces** — `repository.Dao<T>`, a generic interface implemented by
`NotificationRepository`; the JDK's functional interfaces (`Comparator`,
`Runnable`) used throughout via lambdas.

**Generic programming** — `Dao<T>` (a generic interface) and
`util.Rankings` (generic, bounded-type methods: `<K, V extends
Comparable<V>>`), extracted specifically because the same "sort a map by
value" and "find the best entry" logic had been hand-rolled separately in
`TrendsService` and `NaiveBayesClassifier`.

**Collections framework** — `List`/`Set`/`Map` and their implementations
used purposefully rather than defaulting to one everywhere: `ArrayList`
(most lists), `LinkedList` (`TrendsService`'s CGPA accumulator — append then
one full traversal, never random access), `HashSet`/`LinkedHashSet`
(skill sets), `TreeSet` (`CareerPath.skills()` — required skills always
list alphabetically), `EnumSet` (`AuthPages`' self-registerable-role
allow-list), an explicit `Iterator` with `.remove()` (`MentorRepository.save`
— for-each can't safely remove while traversing), `Optional`, records (used
throughout the ML and domain-model code), autoboxing, for-each.

**Strings** — `String`/`StringBuilder` throughout (chosen deliberately over
`StringBuffer` in every HTML-building hot path, since there's no shared
buffer across threads to synchronize); `StringBuffer` used once, in
`SvgCharts`, where that overhead genuinely doesn't matter.

**Exceptions and I/O** — a custom checked exception
(`InvalidProfileException`), try-with-resources file I/O, byte streams
(`HttpExchange`'s `OutputStream`) and character streams
(`BufferedReader`/`BufferedWriter` in `FileManager`), console I/O
(`Scanner` in the console `Menu`), Java 21 pattern matching for switch on
exception type (`WebServer.route`'s error handler picks an HTTP status
from the exception's runtime type, not a chain of `instanceof` checks).

**Multithreading** — a real fixed thread pool (`Executors.newFixedThreadPool`,
`WebServer.start`) so the HTTP server actually dispatches requests
concurrently instead of one at a time; a daemon background thread
(`SessionManager`) that sweeps expired sessions on a timer, using
`ConcurrentHashMap` for thread-safe access from every request thread at once.

**JDBC and DAO** — `NotificationRepository` (see **Persistence** above):
`Connection`/`PreparedStatement`/`ResultSet`, an embedded H2 database, and
the DAO pattern via `Dao<T>`.

**Other** — enums (`User.Role`), switch expressions, recursion (k-d tree
build/search), priority queues, sockets and SSL (`SmtpMailSender`),
`SecureRandom` and `MessageDigest`, reflection (the test runner discovers
`test*` methods at run time instead of listing them by hand), basic
probability/statistics (Gaussian Naive Bayes: per-class mean/variance,
log-space posteriors, softmax normalization).
