# JavaLab

JavaLab is a campus programming-lab and manual evaluation website. The Spring Boot application manages accounts, courses, standalone groups, dated labs, Java submissions, grading, integrity events, and protected monitoring snapshots. The browser editor uses CodeMirror; student Java runs go through a separate constrained Docker runner. Secure-lab still images are analyzed by a separate FastAPI/YOLO service.

## Prerequisites

- JDK 21 and Maven 3.9+
- Docker Desktop or Docker Engine for the isolated Java runner
- Python 3.11+ only when running the monitoring service without Docker

The main app uses a persistent local H2 database by default. PostgreSQL is available through `docker-compose.yml` for campus deployments.

## Start the web application

Create the first administrator from environment settings. The initializer creates the account once and never changes an existing account or password:

```sh
export JAVALAB_INITIAL_ADMIN_NAME='JavaLab Admin'
export JAVALAB_INITIAL_ADMIN_EMAIL='admin@example.edu'
export JAVALAB_INITIAL_ADMIN_PASSWORD='use-a-private-password-of-12-or-more-characters'
mvn spring-boot:run
```

Then open `http://localhost:8080`. The admin creates teacher and student accounts, courses such as `UCS645`, standalone groups such as `3C21`, and course/group assignments. A student may have one group per course; teachers may be assigned to multiple course groups.

For disposable local demo accounts instead, set `JAVALAB_DEMO_ENABLED=true` and a private `JAVALAB_DEMO_PASSWORD` of at least 10 characters. The demo setup is disabled by default and must not be enabled for campus use.

## Start the isolated Java runner

Build the fixed Java 21 image:

```sh
docker build --pull -t javalab-java-runner:21 execution-runner/runner-image
```

Run the separate runner service as a non-root user on a host with Docker access:

```sh
cd execution-runner
export JAVALAB_RUNNER_TOKEN='a-long-private-token-shared-with-the-web-app'
mvn spring-boot:run
```

Set the same `JAVALAB_RUNNER_TOKEN` in the web app. In a multi-host deployment also set `JAVALAB_RUNNER_URL` to the runner's private address and `JAVALAB_RUNNER_BIND` to its private interface. Do not expose the runner to browsers or public networks. Each run uses a fresh container with networking disabled, resource limits, a read-only root, dropped capabilities, a non-root identity, bounded output, and a five-second timeout. If Docker or the prebuilt image is unavailable, the app reports that code execution is unavailable; it never falls back to running student code in the web process.

## Start camera monitoring

The FastAPI service is optional in local development and can be started with its Compose profile:

```sh
export JAVALAB_MONITORING_TOKEN='a-private-token-shared-with-the-web-app'
docker compose --profile monitoring up --build monitoring
```

The default local token is `local-dev-monitoring-token` when the environment variable is unset. For offline campuses, place the approved YOLOv8n weights in a local models directory and set `JAVALAB_YOLO_MODEL` to that mounted path. Otherwise Ultralytics attempts to fetch the lightweight weights at service startup. `/health` reports `unavailable` until the model loads. The service accepts authenticated frames only from Spring Boot, stores annotated images for up to 90 days with a 5,000-image cap, and has no public snapshot links.

## Workflow included

- Admin-created email/password accounts for students, teachers, and admins; BCrypt hashes, session authentication, role checks, and CSRF protection. There is no password-reset flow.
- Course/group assignments and teacher-managed labs with manual open and close controls.
- Java editor with typed stdin, execution output/errors, and periodic draft sync every minute. Run and submit also save the current code. Clipboard copy, cut, and paste are blocked in the editor and logged.
- Students can return to the same active attempt without a rejoin limit. Closing the lab or submitting makes the attempt read-only; reconnecting does not reset the saved code or integrity events.
- Teachers see the assigned roster, periodically refreshed submission status, saved code, run history, timestamped activity, and grade/feedback history. Students see only their own grades and feedback.
- Secure labs count tab switches and visible fullscreen exits together. The fourth event locks the editor and automatically submits the latest saved code. These browser checks are indicators, not a guarantee against external copying.
- Secure camera sessions request browser permission, capture one still at start and every two minutes, and show a truthful camera/inference status. Person/phone detections are indicators for teacher review, not claims of misconduct.
- Student workspace shows DND status while a lab is active. No student-facing notification is emitted during an active session.

The app does not include report downloads or password resets. Campus-only access is a deployment boundary: serve the web app behind campus routing and HTTPS; this project does not identify a physical lab computer or enforce a campus IP allowlist.

## Build and tests

```sh
mvn test
mvn package
```

The Python monitoring tests are in `monitoring-service/tests` and require `pip install -r monitoring-service/requirements-dev.txt` followed by `pytest monitoring-service/tests`.

## Configuration

Main settings use environment variables: `JAVALAB_DATABASE_URL`, `JAVALAB_DATABASE_USERNAME`, `JAVALAB_DATABASE_PASSWORD`, `JAVALAB_RUNNER_URL`, `JAVALAB_RUNNER_TOKEN`, `JAVALAB_MONITORING_URL`, `JAVALAB_MONITORING_TOKEN`, and `JAVALAB_SNAPSHOT_RETENTION_HOURS`. Flyway owns schema migrations; Hibernate validates them on startup.
