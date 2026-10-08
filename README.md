# JavaLab Platform

A Java/Spring Boot web platform for laboratory sessions, code submission, grading and transparent integrity-event logging.

## Requirements

- Java 21+
- Maven 3.9+

## Run

```bash
mvn spring-boot:run
```

Open:

http://localhost:8080

## Demo accounts

Student:
- Email: student@javalab.local
- Password: student123

Teacher:
- Email: teacher@javalab.local
- Password: teacher123

A demo active lab is seeded automatically.

## Build

```bash
mvn clean package
java -jar target/java-lab-platform-0.0.1-SNAPSHOT.jar
```

## Notes

The demo code runner uses `javac` and `java` as separate local processes with compilation/execution timeouts. This is suitable for a local classroom demo, NOT for exposing arbitrary untrusted code execution to the public internet.

For a real college deployment, move execution into isolated containers/VMs with CPU, memory, filesystem and network restrictions.

Secure mode records transparent browser integrity events such as tab switching, copy/paste/cut and fullscreen exits. These are signals for instructor review, not proof of misconduct.
