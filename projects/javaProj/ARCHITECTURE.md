# JavaLab Architecture and Implementation Plan

**Status:** Approved baseline; this document records the implemented local architecture  
**Requirements baseline:** `REQUIREMENTS.md` (approved 2026-11-02)

## 1. Architecture overview

JavaLab uses a server-rendered Spring Boot application as the trusted application boundary. Thymeleaf pages and small vanilla JavaScript modules provide the browser UI. PostgreSQL stores accounts, courses, groups, assignments, labs, submissions, event history, and monitoring metadata. H2 is used only for local development.

Two isolated services support risky/external work:

1. **Java execution runner:** accepts authenticated internal run jobs from Spring Boot and launches a fresh, constrained Docker container for each run. It is reachable only on the private service network. Student code never runs in the Spring Boot JVM.
2. **YOLO monitoring service:** a private FastAPI service receives frames from Spring Boot, performs inference, writes annotated snapshots to private storage, and returns detection metadata. Only Spring Boot may call it; teacher pages request snapshots through Spring Boot authorization checks.

```text
Student/teacher/admin browser
           | HTTPS in deployment; session + CSRF protection
           v
Spring Boot + Thymeleaf ---- PostgreSQL
      |              |
      | private      | private
      v              v
Java runner       FastAPI / YOLO ---- private snapshot storage
      |
      +-- one disposable, restricted Docker container per run
```

The Python and runner services are not browser-facing. During local development, service-to-service HTTP may use localhost or a private Docker Compose network.

## 2. Application modules

Keep one modular Spring Boot application, organized by domain rather than splitting ordinary CRUD into microservices:

- `security`: form login, BCrypt, roles, session handling, CSRF, authorization helpers.
- `account`: users, courses, groups, enrollments, teacher assignments, admin workflows.
- `labs`: lab authoring, group targeting, open/close lifecycle, student lab visibility.
- `submissions`: draft save, run request, submit/lock, grading and feedback.
- `integrity`: resumable submission state, flag counting, and event timeline.
- `monitoring`: camera frame forwarding, snapshot metadata, teacher review state, protected image proxy.
- `execution`: validation and internal runner client; no direct in-process student-code execution.
- `web`: Thymeleaf controllers and page-specific view models.

Controllers handle HTTP input/output. Services enforce authorization and workflow rules. Repositories handle persistence. DTOs are used at API boundaries; JPA entities are not serialized directly.

## 3. Data model

Use UUID or generated numeric primary keys consistently, UTC timestamps stored as instants, foreign keys, and indexes on common dashboard lookups. Course codes, emails, and relationship uniqueness are enforced in the database as well as in services.

### Identity and teaching assignments

- **User**: id, name, email (unique), BCrypt hash, role (`STUDENT`, `TEACHER`, `ADMIN`), optional roll number, enabled flag, created timestamp.
- **Course**: id, code (unique, e.g. `UCS645`), name, optional term/year, active flag.
- **ClassGroup**: id, name (e.g. `3C21`), active flag. A group is standalone and has no `course_id`.
- **StudentEnrollment**: student, course, group, assigned timestamp. Unique `(student_id, course_id)` enforces one group per student per course.
- **TeacherGroupAssignment**: teacher, course, group, assigned timestamp. A teacher can have many assignment rows. The course-group association exists only in assignment records, not on the group.

### Labs and participation

- **LabSession**: id, course, title, question/instructions, scheduled date, secure flag, camera-monitoring flag, status (`UPCOMING`, `ACTIVE`, `CLOSED`, `ARCHIVED`), creator and timestamps. Start/close is manual; timetable integration is future work.
- **LabGroupAssignment**: lab, group, assigned timestamp; unique `(lab_id, group_id)`. The teacher's course/group authorization is validated when creating this link.
- **Submission**: id, student, lab, source code, status (`DRAFT`, `SUBMITTED`, `GRADED`), last saved, submitted, score, feedback, graded by, graded timestamp. Unique `(student_id, lab_id)`; this row is the persistent resumable attempt. Rejoining has no limit. Secure auto-submit updates this record transactionally.
- **RunRecord**: submission/attempt, input, stdout, stderr, exit code, duration, result status, timestamp. Limit output size and retention; don't store arbitrary unbounded output.

### Events and monitoring evidence

- **IntegrityEvent**: id, student, lab, optional submission, event type (`SESSION_STARTED`, `DRAFT_SAVE`, `RUN`, `SUBMIT`, `TAB_SWITCH`, `FULLSCREEN_EXIT`, `COPY`, `PASTE`, `CUT`, `AUTO_SUBMIT`, `CAMERA_UNAVAILABLE`, `CAMERA_CAPTURE`, `GRADE_UPDATED`), server timestamp, optional secure-lab flag number, compact details and idempotency key. Index `(lab_id, student_id, occurred_at)` and `(student_id, occurred_at)` for timelines.
- **MonitoringSnapshot**: id, student, lab, optional submission, private service snapshot reference, captured timestamp, person count, phone count, multiple-person and phone indicators, compact detection JSON. Index `(lab_id, student_id, captured_at)`. Snapshot files expire after the configured 90-day default retention, subject to a 5,000-image cap.
- Prefer `VARCHAR` status/event values and validate them in Java. Store snapshot bytes outside PostgreSQL.

Flyway owns schema evolution. Hibernate uses validation mode outside disposable tests; production startup must not silently change schema.

## 4. Main workflows and invariants

### Draft, run, and submit

- Resolve the logged-in student from the authenticated principal; never trust a browser-supplied student ID.
- A draft save updates only the student's own active lab submission and logs one `DRAFT_SAVE` event after successful persistence. Browser sync is periodic once per minute, with explicit saves before Run and integrity events; keystrokes are not sent to the server.
- Starting an already-started, still-open lab is idempotent: it resumes the same attempt and loads the latest persisted draft and server flag count. Leaving the page or losing connectivity does not create a new attempt or reset state. Rejoin has no count limit while the lab remains open and the attempt is editable. A locked/submitted attempt can be viewed after rejoin but is not made editable by rejoining.
- Run validates the lab/attempt and submission state, then submits bounded code/input to the private runner. Store the returned bounded `RunRecord` and log `RUN`. A run never changes submission status.
- Secure editing starts after a full-screen transition and a recorded `SESSION_STARTED` event. The server rejects secure draft saves, runs, submissions and camera uploads before that event.
- Submit and secure auto-submit use a transaction: lock the submission row, take the newest persisted draft, set source/status/timestamp, append event, and lock the editor. Rejoins resume this row and do not reset flags. Later draft or run calls cannot downgrade or mutate a locked submission.
- Teacher grade updates validate 0–100, record teacher identity and time, emit `GRADE_UPDATED`, and appear on the student's dashboard. Teachers can grade a saved draft or submitted code; grading locks the current code. Students can only fetch their own results.
- Teacher roster, code, activity, grading and monitoring are scoped to the teacher's assigned course/group. Only the lab creator can edit or open/close/archive that lab.

### Integrity events and flag threshold

- Browser code sends authenticated events with a one-time/idempotency key. The server determines actor and lab state, writes event time, and owns the flag count.
- Tab visibility loss is classified as `TAB_SWITCH`; fullscreen loss is classified only when the document remains visible. This avoids counting one background-tab action as both event types. Server idempotency protects retries.
- All labs log events. Only secure labs increment the combined count. On the fourth accepted `TAB_SWITCH`/`FULLSCREEN_EXIT`, a transaction locks the attempt, snapshots the latest draft into the submitted record, records flag number 4 and `SUBMIT`, and returns the authoritative locked/submitted state. The page shows the event log and read-only editor; it does not claim to prevent browser-level evasion.
- Event timeline items are queried per student and displayed in chronological order with local-time rendering, e.g. `19:07 — Fullscreen exited — Flag 1`.

### Camera monitoring

- Secure-lab page checks eligibility and requests browser camera access as the session starts. The browser remains responsible for its permission prompt.
- After access is granted, capture a still immediately, then every two minutes while the page is visible and the attempt remains active. Stop tracks on end, unload, hidden state, lock, or navigation; resume only while the active page is visible. Do not record continuous video.
- If permission, camera hardware, Spring-to-YOLO forwarding, or inference fails, record `CAMERA_UNAVAILABLE`, display an unavailable state, and show the report in the teacher activity dashboard. Never display a green/active state without a successful capture path.
- Spring sends multipart data to FastAPI. FastAPI validates type and size, decodes image, uses the already-loaded model, writes a bounded-retention annotated snapshot, and returns counts, confidences, boxes, timestamp, and opaque snapshot ID. Spring persists metadata and reference.
- Teacher snapshot reads go to Spring Boot, which checks role, group/lab assignment, then fetches from the private Python service and streams the image. No public static snapshot URLs.

## 5. API surface

Use JSON for normal API responses, consistent error bodies (`code`, `message`, optional `fieldErrors`), validation, and role/ownership checks. Form login/logout remain Spring Security endpoints. Browser mutations require CSRF tokens.

### Student

- `GET /api/student/labs/{labId}/status` — lab status, editability and server flag count.
- `POST /api/student/labs/{labId}/events` — idempotent secure-session start, integrity and clipboard activity events.
- `PUT /api/student/labs/{labId}/draft` — latest code draft.
- `POST /api/student/labs/{labId}/runs` — Java source plus stdin; bounded result response.
- `POST /api/student/labs/{labId}/submit`
- `POST /api/student/labs/{labId}/events` — integrity/copy activity events.
- `POST /api/student/labs/{labId}/monitoring/frames` — authenticated multipart image, secure attempt only.
- `GET /api/student/submissions` and `GET /api/student/submissions/{id}` — own records/grades only.

### Teacher

- Teacher pages use authenticated form routes under `/teacher/**`, including lab open/close, roster, grade, saved-code run, activity timeline and protected snapshot review.
- `GET /teacher/snapshots/{snapshotId}/image` — protected snapshot stream, with role and assigned-student checks.

### Admin

- CRUD for accounts, courses, standalone groups, student enrollment and teacher/group/course assignments.
- Admin routes verify role and prevent duplicate/invalid assignments.

### Python service (private network only)

- `GET /health`
- `POST /analyze` multipart frame; bounded upload and inference response.
- `GET /snapshots/{snapshot_id}` internal retrieval only, authenticated between services.

## 6. Frontend pages

Thymeleaf templates with shared accessible layout and small page-specific JavaScript; no React.

- Login page with clear credential errors.
- Student dashboard: profile, dated group labs, status, own grade/feedback, DND state while active.
- Student lab workspace: question, editor, input, run controls, output/errors, autosave indicator, submit state, secure instructions, fullscreen and camera state, event warnings.
- Teacher dashboard: group/lab selector, open/close controls, student roster, saved/submitted status, scores, and per-student timestamped activity rows.
- Teacher lab setup/edit page.
- Teacher student-review page: code, run records, grade, event timeline with flags, and snapshot timeline/gallery.
- Admin pages: account creation, course/group setup, enrollment and teacher assignments.

Package editor assets locally (for example CodeMirror via a pinned WebJar) so the lab editor does not depend on a public CDN during an exam. Clipboard handlers block editor copy/paste/cut and log attempts where the browser exposes them; documentation states their limits.

## 7. Security and reliability boundaries

- Spring Security roles and service-level ownership checks on every protected operation; CSRF on browser mutations; BCrypt; HttpOnly/SameSite session cookies; set `JAVALAB_SESSION_COOKIE_SECURE=true` behind production HTTPS. There is no password-reset or public-registration flow.
- Validate lab status, user scope, score bounds, Java source length, stdin length, upload MIME and decoded image dimensions, metadata size, runner outputs, and all IDs.
- Runner has no student-supplied Docker flags or image names. Use a fixed JDK image, random temp directory, non-root container user, no network, read-only root, dropped capabilities, no-new-privileges, CPU/memory/PID limits, bounded output, timeout, and cleanup. Run the runner on an isolated host/VM; access to a Docker daemon remains a sensitive infrastructure boundary.
- FastAPI model is loaded once at startup. Configure model path, confidence threshold, upload limits, private snapshot directory, and retention via environment. Snapshot files expire after 90 days by default and are capped at 5,000 images. Do not commit weights or snapshots.
- Errors return safe messages; detailed diagnostics are server-side. Service outages produce recoverable UI states and do not fake a successful run, save, submission, or monitoring result.
- Campus-only routing without an allowlist does not prove physical lab-computer identity. Web UI controls cannot enforce OS-level DND or prevent external copying. The app shows DND while a lab is active and does not issue student-facing notifications.

## 8. Project structure

```text
javaProj/
  pom.xml
  README.md
  docker-compose.yml
  src/main/java/com/javalab/
    account/ labs/ submissions/ integrity/ monitoring/ execution/
    security/ config/ web/ common/
  src/main/resources/
    application.yml
    db/migration/
    templates/ static/css/ static/js/ static/vendor/
  src/test/java/com/javalab/
  monitoring-service/
    main.py tests/ requirements.txt Dockerfile
    data/snapshots/             # runtime volume only
  execution-runner/
    src/ runner-image/ Dockerfile
```

The runner is implemented as a private Java service with a narrow job API. The monitoring service is Python/FastAPI; both remain outside the browser-facing application.

## 9. Dependencies and configuration

- Main app: Spring Boot Web, Thymeleaf, Security, Data JPA, Validation, Actuator health, Flyway, PostgreSQL driver, H2 runtime for dev, test support, and a pinned editor asset dependency.
- Runner: Java 21/JDK 21 fixed execution image, Docker runtime on isolated execution host, internal authenticated API.
- YOLO: Python 3.11+, FastAPI, Uvicorn, Ultralytics, OpenCV headless, NumPy, multipart parser, Pillow for image validation if needed.
- Environment configuration: DB URL/credentials, session secret, runner URL/token, YOLO URL/token, model path/confidence, upload size, snapshot directory/retention, execution timeout and resource caps. No production credentials in source control.

## 10. Verification strategy

Automated tests cover the workflows below. Report only commands that were actually run, distinguish mocked service tests from Docker/model/browser checks, and do not describe unverified infrastructure behavior as proven.

- **Unit/service tests:** group/course scope, lab state transitions, draft/submission locking, idempotent submit, score validation, flag threshold transaction, correct flag rendering data, camera failure event handling.
- **Spring MVC/security tests:** login roles, student cannot read another student's records, teacher cannot cross assigned groups, admin-only account/assignment endpoints, CSRF and invalid input, protected snapshot proxy.
- **Persistence/integration tests:** Flyway schema on H2-compatible test profile or PostgreSQL Testcontainers if available; one enrollment per student/course; student submission/grade visibility; event ordering and persistence; records survive app context restart.
- **Runner tests:** mock runner contract at app layer; runner integration tests for stdin/stdout, compilation/runtime errors, timeout, bounded output, disabled network and cleanup. Docker-dependent tests run only when Docker is available and say so clearly.
- **YOLO tests:** FastAPI health, upload size/type and corrupt-image rejection, mocked model response for person/phone, annotated snapshot storage/retrieval, retention behavior. Model inference should also have an optional manual smoke test requiring weights.
- **Browser workflow checks:** login, lab open/close, autosave, run, submit lock, event ordering and flag 4 behavior, camera permission denial/unavailable state, teacher review. Camera hardware and true fullscreen behavior require manual browser verification.

## 11. Implemented scope and remaining deployment checks

The application, runner, and monitoring-service source for the approved first-release scope is in this repository. These checks require deployment infrastructure or a real browser/device:

- Docker-backed student code execution requires the runner image and a working, isolated Docker host.
- Real YOLO detections require model weights and a running monitoring service; local development may need network access to fetch weights.
- Camera permission, fullscreen transitions, and visibility events require manual browser testing on lab hardware.
- Campus-only routing, HTTPS, database backups, account provisioning, model licensing, and retention policy must be configured by the campus deployment.

## 12. Manual deployment checks

Before production, confirm the campus server/network routing, HTTPS certificate, database backups, account provisioning policy, Docker runner host isolation, model weight licensing/source, camera policy and the institution's actual retention requirements. These deployment checks do not change the approved feature scope.
