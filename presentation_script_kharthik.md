# CareerCompass — Presentation Script (Kharthik)

*Target: 3–4 minutes. ~500 words. Speak at a natural pace; pause where marked.*

---

**[Open — the problem]**

Good morning everyone. Most students I know don't struggle because they lack
ability — they struggle because nobody tells them, clearly, *which* direction
actually fits them and *what* to do next. Career advice is either generic
"follow your passion" talk, or a black-box quiz that spits out an answer with
no reason behind it. So my teammate and I built **CareerCompass** — a career
guidance platform that gives students a direction, shows its work, and then
connects them to the people who can help them get there.

**[What it is]**

CareerCompass is a five-role web application, and here's the part I'm proud of:
it's built on plain **Java 21 with zero external libraries**. No Spring, no
frameworks, no build tools. The web server, the email client, and the entire
machine-learning pipeline are all written from scratch. If it runs, it's
because we wrote it.

**[The ML — the core]**

The heart of the product is the recommendation engine. For any student, it
scores six career domains and blends three signals. Half the score is a
**transparent rule engine** — skill overlap and interest alignment. The other
half comes from **two different machine-learning models we wrote by hand**:
a **k-nearest-neighbours** classifier, which is instance-based — it compares a
student to the profiles most similar to them — and a **Naive Bayes**
classifier, which is probabilistic — it learns a distribution for each domain
and asks which one best explains the student. *(pause)* Using two different
learning paradigms matters: when two models built on completely different math
agree on a direction, that's far stronger evidence than one model alone.

And crucially — every recommendation **shows its reasoning**. The student sees
exactly how much came from rules, from k-NN, and from Bayes, plus a
peer-percentile benchmark. The ML adds intelligence without becoming a black
box.

**[Closing the loop — the five roles]**

But a recommendation on its own changes nothing. So CareerCompass closes the
loop across five roles. **Students** get their direction, a month-by-month
skill-gap plan, and one-click internship applications. **Recruiters** browse
candidates ranked by best fit, with faculty-verified skills flagged.
**Mentors** — industry professionals — accept mentorship requests.
**Faculty** endorse student skills, and a verified skill is literally weighted
higher in the engine. And **admins** manage the platform. Every cross-role
action — a shortlist, an endorsement, a mentor reply — fires a notification,
so nothing happens in a silo.

**[Engineering credibility]**

One more thing worth mentioning. We also built a k-d tree from scratch to
speed up the nearest-neighbour search, and we verified it against brute force
across sixty thousand randomized trials. That process actually caught two real
bugs — which, honestly, is the point: we can *prove* our code is correct, not
just claim it.

**[Close]**

CareerCompass turns "I don't know what to do" into a direction, a plan, and a
network — all explainable, all in pure Java. Thank you. I'm happy to take any
questions.
