package com.javalab.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:account-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "javalab.demo.enabled=false"
})
@Transactional
class AccountAdminServiceTest {
    @Autowired AccountAdminService service;
    @Autowired UserAccountRepository users;
    @Autowired StudentEnrollmentRepository enrollments;

    @Test void createsHashedUserAndEnforcesOneGroupPerCourse() {
        Course course = service.createCourse("ucs645", "Java Programming");
        StudyGroup first = service.createGroup("3c21");
        service.createGroup("3c42");
        UserAccount student = service.createUser("Aarav Mehta", "AARAV@example.edu", "safe-test-password", Role.STUDENT, "R001");
        assertThat(student.getEmail()).isEqualTo("aarav@example.edu");
        assertThat(student.getPassword()).isNotEqualTo("safe-test-password");

        service.assignStudent(student.getId(), course.getCode(), first.getName());
        assertThat(enrollments.findByStudentIdOrderByCourseCodeAsc(student.getId())).hasSize(1);
        assertThatIllegalArgumentException().isThrownBy(() -> service.assignStudent(student.getId(), course.getCode(), "3C42"))
            .withMessageContaining("already has a group");
    }

    @Test void teacherCanBeAssignedToMultipleGroups() {
        Course course = service.createCourse("UCS710", "Programming II");
        StudyGroup a = service.createGroup("4C21");
        StudyGroup b = service.createGroup("4C22");
        UserAccount teacher = service.createUser("Teacher One", "teacher@example.edu", "safe-test-password", Role.TEACHER, null);

        service.assignTeacher(teacher.getId(), course.getCode(), a.getName());
        service.assignTeacher(teacher.getId(), course.getCode(), b.getName());
        assertThat(users.findById(teacher.getId())).isPresent();
    }
}
