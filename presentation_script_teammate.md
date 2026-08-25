# CareerCompass — Presentation Script (Teammate)

*Target: 3–4 minutes. ~500 words. Speak at a natural pace; pause where marked.*

---

**[Open — the problem]**

Hi everyone. Think back to when you were choosing a career path. For most
students, the hard part isn't talent — it's uncertainty. The advice out there
is either vague motivational stuff, or an online quiz that hands you a label
with no explanation of how it got there. Neither actually helps you decide.
That's the gap we set out to close with **CareerCompass** — a platform that
points a student in a clear direction, explains *why*, and then links them up
with the people who can help them act on it.

**[What it is]**

At its core, CareerCompass is a web application serving five different kinds of
users. What makes it unusual under the hood: the entire thing is written in
**pure Java 21 — no external libraries at all**. We didn't pull in Spring or
any framework. The HTTP server, the email system, and every bit of the
machine-learning is hand-coded. Nothing is outsourced to a library.

**[The ML — the core]**

Let me spend a moment on the recommendation engine, because that's the brain of
the system. Given a student's profile, it ranks six career domains using three
combined signals. Fifty percent is a **rule-based score** we designed —
matching a student's skills and interests against each field. The remaining
fifty percent comes from **two hand-built machine-learning models**. The first
is **k-nearest-neighbours**, which works by similarity — it finds the training
profiles closest to the student and lets them vote. The second is **Naive
Bayes**, which works by probability — it models each career as a distribution
and calculates which one most likely produced this student. *(pause)* We
deliberately used two *different* approaches, because if a similarity-based
model and a probability-based model land on the same answer, you can trust that
answer a lot more.

And the whole thing is **explainable by design**. Instead of just a result, the
student sees the exact contribution of each method and how they compare to
their peers. Smart, but never a black box.

**[Closing the loop — the five roles]**

Of course, knowing your direction isn't enough — you have to act on it. That's
why CareerCompass is built around five connected roles. **Students** get
recommendations, a step-by-step plan to close their skill gaps, and internship
applications. **Recruiters** search candidates sorted by fit and see which
skills are faculty-verified. **Mentors** from industry take on students who
request them. **Faculty** endorse real skills — and those endorsements
actually boost a student's score in the engine. **Admins** oversee it all. And
every interaction between roles triggers a notification, so the whole system
stays connected rather than fragmented.

**[Engineering credibility]**

Quick note on rigor: to make the ML search efficient, we also implemented a
k-d tree from scratch and stress-tested it against a brute-force baseline over
tens of thousands of trials. That testing caught genuine bugs — which is
exactly why we did it. We wanted correctness we could demonstrate, not just
assert.

**[Close]**

In short, CareerCompass takes a student from confusion to a clear, explainable
plan and a real support network — all delivered in pure Java. Thank you, and
I'd be glad to answer any questions.
