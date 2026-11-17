package com.javalab.config;
import com.javalab.account.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
@ConditionalOnProperty(name="javalab.demo.enabled", havingValue="true")
public class DemoDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private final UserAccountRepository users;
    private final CourseRepository courses;
    private final StudyGroupRepository groups;
    private final StudentEnrollmentRepository enrollments;
    private final TeacherGroupAssignmentRepository assignments;
    private final PasswordEncoder encoder;
    private final Environment environment;
    public DemoDataInitializer(UserAccountRepository users, CourseRepository courses, StudyGroupRepository groups,
        StudentEnrollmentRepository enrollments, TeacherGroupAssignmentRepository assignments, PasswordEncoder encoder, Environment environment) {
        this.users=users; this.courses=courses; this.groups=groups; this.enrollments=enrollments; this.assignments=assignments; this.encoder=encoder; this.environment=environment;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        String password = environment.getProperty("JAVALAB_DEMO_PASSWORD", "");
        if (password.length() < 10) {
            log.error("Demo data is enabled but JAVALAB_DEMO_PASSWORD is missing or shorter than 10 characters; demo records were not created.");
            return;
        }
        UserAccount teacher = account("teacher@javalab.local", "Demo Teacher", Role.TEACHER, null, password);
        UserAccount s1 = account("student1@javalab.local", "Aarav Mehta", Role.STUDENT, "3C21-001", password);
        UserAccount s2 = account("student2@javalab.local", "Mira Shah", Role.STUDENT, "3C21-002", password);
        UserAccount s3 = account("student3@javalab.local", "Kabir Rao", Role.STUDENT, "3C42-001", password);
        account("admin@javalab.local", "JavaLab Admin", Role.ADMIN, null, password);
        Course course = courses.findByCodeIgnoreCase("UCS645").orElseGet(() -> courses.save(new Course("UCS645", "Java Programming")));
        StudyGroup g1 = groups.findByNameIgnoreCase("3C21").orElseGet(() -> groups.save(new StudyGroup("3C21")));
        StudyGroup g2 = groups.findByNameIgnoreCase("3C42").orElseGet(() -> groups.save(new StudyGroup("3C42")));
        enroll(s1, course, g1); enroll(s2, course, g1); enroll(s3, course, g2);
        assign(teacher, course, g1); assign(teacher, course, g2);
        log.info("Development demo records are available; password is supplied by JAVALAB_DEMO_PASSWORD.");
    }
    private UserAccount account(String email, String name, Role role, String roll, String password) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(new UserAccount(name, email, encoder.encode(password), role, roll)));
    }
    private void enroll(UserAccount student, Course course, StudyGroup group) {
        if (!enrollments.existsByStudentIdAndCourseId(student.getId(), course.getId())) enrollments.save(new StudentEnrollment(student, course, group));
    }
    private void assign(UserAccount teacher, Course course, StudyGroup group) {
        if (!assignments.existsByTeacherIdAndCourseIdAndGroupId(teacher.getId(), course.getId(), group.getId()))
            assignments.save(new TeacherGroupAssignment(teacher, course, group));
    }
}
