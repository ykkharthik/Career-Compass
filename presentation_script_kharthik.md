# CareerCompass — Presentation Script (Kharthik)

*Target: 3–4 minutes. ~500 words. Speak at a natural, conversational pace —
this is written to be talked, not read. Pause where marked.*

---

Good morning/afternoon everyone, thank you for having me. Most students
end up choosing a career path through one of two ways: vague "follow your
passion" advice, or an online quiz that hands them a result with no
explanation behind it. Neither of those actually helps someone make a
real decision. That's the gap my teammate and I wanted to close, and
that's where CareerCompass came from.

So here's what it actually does. You sign up as a student, put in your
CGPA, your skills, rate a few interests, and it gives you a ranked list of
career paths — but not just a label with no reasoning behind it. It shows
you exactly *why*: which skills you already have, how your interests line
up, and even where you stand compared to other students with a similar
profile. Nothing here is a black box — every single recommendation comes
with the reasons behind it, right there on the page.

*(pause)* And that's really just the starting point, because knowing your
direction doesn't help much if you can't act on it. So once you get a
recommendation, you also get a month-by-month plan for the skills you're
missing, a list of internships that actually match, and you can apply with
one click and track exactly where your application stands — applied,
shortlisted, interview, offer, all of it.

But students are only one piece. The whole idea behind CareerCompass is
bringing five different kinds of people onto one platform instead of
leaving them all in separate silos. Recruiters can browse candidates and
see who's the strongest fit — and this is the part I like most — they can
see which skills are actually *verified* by faculty, not just typed in by
the student. Mentors, real industry professionals, can take on students
who reach out asking for guidance. Faculty go through student profiles and
endorse the skills they can vouch for, and that endorsement genuinely
pushes that student's score up and makes them stand out more to
recruiters. And admins keep the whole thing running. Any time something
happens to you — you get shortlisted, a mentor replies, a skill gets
endorsed — you get notified. Nothing just disappears into the system.

*(pause)* Now, a quick word on how it's actually built, because I think
it's worth mentioning: everything you're seeing — the recommendation
logic, the web server itself, even the email verification — we wrote from
scratch in plain Java. No frameworks doing the heavy lifting for us. That
includes two different machine learning approaches running underneath the
recommendations, and we didn't just assume our own code was correct — we
stress-tested it tens of thousands of times against itself, and that
process actually caught two real bugs we had to go fix ourselves. So when
I say this thing works, I mean we checked.

So at the end of the day, CareerCompass takes someone from "I genuinely
don't know what to do with my life" to an actual direction, a plan to get
there, and real people around them to help make it happen. Thanks — happy
to take any questions.
