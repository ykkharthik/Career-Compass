# CareerCompass – Career Guidance Platform

CS5304 Java Programming project (Chennai Institute of Technology).
A five-role web application (and still a console app), built on **JDK 21 with
zero external libraries** — the HTTP server, the SMTP mail client, and every
piece of the machine-learning pipeline are all written in plain Java.

## Run the web app

```
javac -d out $(find src -name "*.java")
java -cp out web.WebMain
```

Then open **http://localhost:8080**. Use `java -cp out web.WebMain 9090` for a
different port. Run from the project root so `data/` is found.

Windows (PowerShell), if `find` is unavailable:
```
javac -d out (Get-ChildItem -Recurse -Filter *.java src | % FullName)
java -cp out web.WebMain
```

The console version still works: `java -cp out Main`

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
- **Session expiry**: sessions idle out after 30 minutes
- **CSRF protection**: every authenticated state-changing form carries a
  per-session token, checked on submit
- Role-based access control enforced on every route, not just hidden nav links

## The ML component — pure Java, no libraries

`ml/KnnCareerClassifier` implements k-nearest-neighbours from scratch:

1. `data/career_training.csv` holds 43 labelled example profiles
   (5 interest ratings + scaled CGPA → career domain).
2. A student's feature vector is compared to every example by **Euclidean
   distance**; the nearest k examples vote on the domain.
3. The final ranking blends this 50/50 with a transparent rule-based score
   (skill overlap, weighted higher for faculty-endorsed skills, + interest
   alignment).

Every recommendation shows both score components and the reasons behind them
— the ML adds a learning signal without becoming a black box.

**`ml/KdTree`** is a from-scratch k-d tree that answers the same nearest-
neighbour queries in expected O(log n) instead of brute force's O(n). It uses
a deterministic secondary sort key so that ties resolve identically to the
brute-force scan. Verification during development: 0 mismatches against
brute force across 10,000 randomized trials, all 43 training points as
queries, 5 hand-picked edge cases (exact duplicates, corner values), and 6
different values of k — a sign error in the tie-break comparator was caught
this way partway through development (it initially inverted the eviction
condition), which is worth mentioning in viva if asked how correctness was
established rather than just asserted. `kdNearestNeighbours()` is available
on the classifier for any caller that needs the faster lookup as the training
set scales past a few dozen rows; the shipped predictions currently use the
brute-force path since correctness there needs no argument at all.

## Interface design

Hand-written HTML and CSS (no Bootstrap, no React, no build step), themed as
a navigation chart because the engine literally computes a bearing and a
distance: chart-paper graticule, deep ink panel, magenta for markers (the
colour real charts reserve for aids to navigation), monospaced readouts for
every number. The signature element is the **bearing tape** on the
recommendations page — a graduated scale with a needle at each domain's
score. A hand-built SVG radar chart visualizes each student's five interest
dimensions with no charting library.

## Package structure

```
src/
├── Main.java                  console entry point
├── web/
│   ├── WebMain.java           web entry point  → http://localhost:8080
│   ├── WebServer.java         routes + HTML rendering (com.sun.net.httpserver)
│   ├── SessionManager.java    cookie sessions, CSRF tokens, idle expiry
│   ├── SvgCharts.java         hand-built interest radar chart
│   └── Pages.java             CSS + page shell
├── auth/                      User, AuthService (register/login/lockout),
│                              EmailVerifier, MailService, SmtpMailSender
├── model/                     Student, abstract CareerPath + 6 subclasses,
│                              Certification, Internship, Mentor,
│                              MentorshipRequest, Endorsement, Faculty,
│                              Notification, Application
├── repository/                FileManager (CSV I/O) + one repository per
│                              entity above, all CRUD over CSV
├── ml/                        KnnCareerClassifier, KdTree
├── service/                   RecommendationService (hybrid + endorsement
│                              weighting), SkillGapService,
│                              CertificationAdvisor, InternshipAdvisor,
│                              PercentileService, TrendsService
├── recruiter/                 RecruiterPortal (console recruiter view)
├── menu/                      Menu (console UI)
└── exception/                 InvalidProfileException
data/
├── career_training.csv        43 labelled profiles for k-NN
├── certifications.csv         28 certifications across 6 domains
├── internships.csv            20 internship listings with prerequisites
├── users.csv / students.csv   accounts and profiles
├── mentors.csv / mentorship_requests.csv
├── faculty.csv / endorsements.csv
├── applications.csv / notifications.csv
└── mail.properties.example    template for real email sending
```

## Java concepts demonstrated

Inheritance and polymorphism (CareerPath hierarchy) · abstract classes ·
records (used throughout the ML and domain-model code) · interfaces and
lambdas · collections and streams · custom checked exception ·
try-with-resources file I/O · enums · Optional · switch expressions ·
recursion (k-d tree build/search) · priority queues · multithreading (HTTP
thread pool) · sockets and SSL · `SecureRandom` and `MessageDigest` · HTTP
request handling.
