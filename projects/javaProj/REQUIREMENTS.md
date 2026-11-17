# JavaLab Requirements

**Status:** Approved baseline  
**Date:** 2026-11-02

## 1. Purpose

JavaLab is a campus-hosted website for university programming labs and practical evaluations. It provides a browser Java IDE, student drafts and submissions, teacher review and manual grading, and secure-session monitoring. Java is also the application backend language.

## 2. Roles and access

- **Admin:** Creates user accounts and manages class groups and membership assignments. There is no public registration or password-reset flow in the first version.
- **Teacher:** Is assigned to one or more groups and manages labs and students only for assigned groups. Teachers create labs, manually open and close them, monitor saved drafts and submissions, run submitted code, grade work, and review activity and camera evidence.
- **Student:** Belongs to one group per course. Students see labs for their group, work on open labs, submit code, and see their own grades and feedback.
- Sign-in uses email and password. Passwords are stored as BCrypt hashes. Backend authorization must enforce role and record ownership.

## 3. Groups and lab workflow

- Courses have codes such as `UCS645`. Groups have names such as `3C21`, `3C41`, or `3C42`; groups are standalone and are not owned by courses.
- Admins create courses and groups, then manage course-group-user assignments separately. An assignment/enrollment links a user, a course, and a group without making the group a child of the course. A student can belong to one group per course; a teacher can be assigned to many groups (and their course contexts).
- Teachers create a lab with a title, question/instructions, date, and assigned group(s), then manually open and close it. Timetable integration is a later enhancement.
- Students see a dated lab list with its status. Students can enter a lab only while it is open, subject to the lab's secure-session requirements. Leaving the page or losing connectivity does not consume an attempt: students may rejoin an open lab without a rejoin limit and resume their existing saved work and session state.
- The student dashboard shows labs and that student's graded results. Teacher dashboards show group-scoped lab activity and submissions.

## 4. Student coding and submission

- The first release supports **Java only**. The browser provides a Java code editor, a standard-input field, Run, Save Draft, and Submit actions.
- Run results show standard output, compilation/runtime errors, and execution status. Runs have a five-second timeout.
- Java execution must occur in an isolated Docker sandbox, not in the Spring Boot process. The sandbox must have no network access, no privileged mode, resource/process limits, and temporary workspace cleanup.
- Drafts are saved with a debounce/autosave mechanism and surfaced to the teacher dashboard as saved drafts, not as keystroke tracking.
- Submission stores final source and timestamp, becomes immutable to the student, and cannot be downgraded to a draft by later autosave or Run calls. Rejoining after a disconnect resumes the same attempt; it does not create a blank attempt or reset integrity flags. Teacher reopening after lock/submission, if supported, must be explicit and audited.
- Teachers can inspect and run saved/submitted Java code. Grading is manual in the first release, with a 0–100 score and written feedback. Students see only their own graded results.

## 5. Integrity monitoring

- Tab switches and fullscreen exits are logged in **every lab** and visible in the teacher dashboard. Copy and paste are blocked in every lab's in-browser editor.
- Secure labs add a combined violation counter for tab switches and fullscreen exits. On the **fourth combined violation** (more than three), the editor becomes read-only and the current code is automatically submitted. The system records the trigger and does not silently discard the code.
- The server is authoritative for persistent secure-lab violation counts. Each event is logged for teacher review. No additional penalty is applied automatically.
- Student-facing app notifications are suppressed (DND) during an active lab session. Submissions, auto-submissions, integrity events, and camera availability reports appear in the teacher's activity dashboard; no push-notification delivery is claimed.
- Browser controls are limited: editor-level clipboard blocking, fullscreen requests, and visibility events cannot prevent every external method of copying or detect every kind of window focus change. This is not a guarantee against cheating.

## 6. Camera and YOLO monitoring

- Camera monitoring runs **only in secure labs**. At secure-session start the browser requests camera permission and starts capture after permission is granted. The browser's permission prompt remains required; the application does not bypass it.
- Capture one still image every **two minutes**, not continuous video. Stop capture and camera tracks when the session ends or the page is left/hidden; do not upload after the session is locked.
- The page must show a truthful camera status. If permission is denied, hardware fails, or the service is unavailable, show that monitoring is unavailable and record the event for the teacher dashboard; never claim it is active.
- Spring Boot receives authenticated frames and forwards them to a separate Python FastAPI service. The service loads YOLO once and detects COCO person and cell-phone classes, returning counts, bounding boxes, confidence, timestamp, and annotated snapshot reference.
- Detection results are indicators for teacher review, not proof of misconduct and do not cause automatic penalties.
- Snapshots are stored outside the database; metadata and protected references are stored in the database. Snapshot retention is configurable and defaults to **90 days**, subject to a 5,000-image cap. Activity, submission, grading, and monitoring metadata are retained as institutional records unless an admin removes them under an explicit policy.
- Only authorized teachers/admins may retrieve snapshots. Python snapshot routes are not exposed directly to unauthenticated browsers; Spring Boot checks access before serving evidence.

## 7. Teacher experience

- Teachers see group-scoped labs, student participation, saved-draft/submission status, last activity, grading status, and scores.
- The teacher dashboard includes a timestamped, chronological activity log nested under each student. Integrity rows show the event and flag count in a concise format, for example: `19:07 — Fullscreen exited — Flag 1`.
- Student review includes profile, course/group assignments, saved and submitted code, run output/errors, submission time, the same activity timeline, integrity events, and chronological monitoring snapshots.
- Teachers can inspect monitoring snapshots and detections. Model output alone does not affect a student's grade.
- No downloadable lab reports are required for the first release.

## 8. Interface and notifications

- Deliver a responsive website for desktop lab computers, with tablet support where practical. The coding workspace prioritizes the editor, question, input, and output.
- Use separate student, teacher, admin, lab workspace, and individual review experiences with accessible contrast and clear status states.
- DND suppresses student app notifications while the student is in an active lab. Teacher dashboards show submissions, secure auto-submissions, integrity activity, and camera availability reports.

## 9. Technology and hosting

- Main application: Java 21, Spring Boot 3, Spring MVC/Security/Data JPA, Maven, Thymeleaf, HTML/CSS/vanilla JavaScript.
- Database: H2 for local development; PostgreSQL for persistent campus deployment. Use Flyway migrations; do not rely on Hibernate schema mutation in production.
- Monitoring microservice: Python 3.11+, FastAPI, Ultralytics YOLO (lightweight compatible model), OpenCV, NumPy, Uvicorn.
- Code execution: isolated Docker-based Java runner with network disabled and CPU, memory, process, filesystem, and timeout controls.
- Host on a university server reachable only from the campus network. No machine/IP allowlist is included in this release.

## 10. Out of scope for first release

- C and C++ execution (deferred; Java only for now).
- Timetable integration (manual teacher scheduling/open/close first).
- Automatic test-case grading (manual teacher grading first).
- Password reset and self-registration.
- Downloadable reports.
- Network/IP allowlisting and strict identification of physical lab computers.

## 11. Important limitations and unresolved deployment details

- Campus-network-only hosting without a machine or network allowlist cannot guarantee that only physical lab computers can access the site; it means access from the campus network. The user chose to skip allowlisting for now.
- Camera permission is controlled by the browser and must be granted. Hardware failure or denial is surfaced to the teacher; the application must not misrepresent monitoring state.
- YOLO's standard model can detect visible people and cell phones but cannot establish cheating or detect every device/person.
- A Docker runtime must be available and configured on the execution host. If unavailable, Java execution must be clearly marked development-only and must not be described as secure.
- Snapshot retention defaults to 90 days, is configurable, and is also capped at 5,000 images. Campus policy may require a different period before production deployment.

## 12. Acceptance workflow

1. Admin creates accounts, courses/groups, and group assignments.
2. Teacher creates a dated Java lab for an assigned group and manually opens it.
3. Student signs in and opens the active lab; after leaving or a network interruption, they can rejoin without a rejoin limit and resume the same work.
4. Student writes Java, supplies input, runs code, sees output/errors, and saves a draft.
5. Teacher sees the saved draft and a timestamped, per-student activity log (including integrity event flag numbers) without keystroke-level surveillance.
6. For secure labs, camera permission is requested, truthful camera status is shown, and still frames are captured every two minutes and forwarded via Spring Boot to YOLO.
7. Activity events and detection metadata are persisted and available only to authorized reviewers.
8. The fourth combined tab/fullscreen violation in a secure lab makes code read-only and auto-submits it; no code is lost.
9. Student can submit manually; a submitted solution cannot revert to draft.
10. Teacher reviews/runs the code, grades it manually, and leaves feedback.
11. Student sees only their own grade and feedback.
12. Records persist across application restart.

## 13. Approval

The user approved this baseline on 2026-11-02. Future changes to scope should be recorded as amendments.
