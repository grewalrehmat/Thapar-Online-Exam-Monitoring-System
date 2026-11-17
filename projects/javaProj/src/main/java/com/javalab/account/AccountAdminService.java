package com.javalab.account;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
public class AccountAdminService {
    private final UserAccountRepository users;
    private final CourseRepository courses;
    private final StudyGroupRepository groups;
    private final StudentEnrollmentRepository enrollments;
    private final TeacherGroupAssignmentRepository assignments;
    private final PasswordEncoder encoder;

    public AccountAdminService(UserAccountRepository users, CourseRepository courses, StudyGroupRepository groups,
                               StudentEnrollmentRepository enrollments, TeacherGroupAssignmentRepository assignments,
                               PasswordEncoder encoder) {
        this.users = users; this.courses = courses; this.groups = groups;
        this.enrollments = enrollments; this.assignments = assignments; this.encoder = encoder;
    }

    @Transactional
    public UserAccount createUser(String name, String email, String rawPassword, Role role, String rollNumber) {
        if (name == null || name.isBlank() || email == null || email.isBlank() || role == null)
            throw new IllegalArgumentException("Name, email, and role are required.");
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > 254 || !normalizedEmail.matches("[^@ ]+@[^@ ]+\\.[^@ ]+"))
            throw new IllegalArgumentException("Enter a valid email address.");
        if (name.trim().length() > 120) throw new IllegalArgumentException("Name must be 120 characters or fewer.");
        if (users.existsByEmailIgnoreCase(normalizedEmail)) throw new IllegalArgumentException("That email is already in use.");
        if (rawPassword == null || rawPassword.length() < 10 || rawPassword.length() > 72)
            throw new IllegalArgumentException("Password must be between 10 and 72 characters.");
        if (role == Role.STUDENT && (rollNumber == null || rollNumber.isBlank()))
            throw new IllegalArgumentException("Student accounts need a roll number.");
        if (role == Role.STUDENT && rollNumber.trim().length() > 40)
            throw new IllegalArgumentException("Roll number must be 40 characters or fewer.");
        return users.save(new UserAccount(name.trim(), normalizedEmail, encoder.encode(rawPassword), role,
                role == Role.STUDENT ? rollNumber.trim() : null));
    }

    @Transactional
    public Course createCourse(String code, String name) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) throw new IllegalArgumentException("Course code and name are required.");
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 30 || name.trim().length() > 140 || !normalized.matches("[A-Z0-9-]+"))
            throw new IllegalArgumentException("Use a valid course code (letters, numbers, hyphens) and a name of at most 140 characters.");
        if (courses.existsByCodeIgnoreCase(normalized)) throw new IllegalArgumentException("That course code already exists.");
        return courses.save(new Course(normalized, name.trim()));
    }

    @Transactional
    public StudyGroup createGroup(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Group name is required.");
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 40 || !normalized.matches("[A-Z0-9-]+"))
            throw new IllegalArgumentException("Use a valid group name (letters, numbers, hyphens; at most 40 characters).");
        if (groups.existsByNameIgnoreCase(normalized)) throw new IllegalArgumentException("That group already exists.");
        return groups.save(new StudyGroup(normalized));
    }

    @Transactional
    public void assignStudent(Long userId, String courseCode, String groupName) {
        UserAccount student = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (student.getRole() != Role.STUDENT) throw new IllegalArgumentException("Select a student account.");
        Course course = courses.findByCodeIgnoreCase(courseCode.trim()).orElseThrow(() -> new IllegalArgumentException("Course not found."));
        StudyGroup group = groups.findByNameIgnoreCase(groupName.trim()).orElseThrow(() -> new IllegalArgumentException("Group not found."));
        if (enrollments.existsByStudentIdAndCourseId(student.getId(), course.getId()))
            throw new IllegalArgumentException("This student already has a group in that course.");
        enrollments.save(new StudentEnrollment(student, course, group));
    }

    @Transactional
    public void assignTeacher(Long userId, String courseCode, String groupName) {
        UserAccount teacher = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (teacher.getRole() != Role.TEACHER) throw new IllegalArgumentException("Select a teacher account.");
        Course course = courses.findByCodeIgnoreCase(courseCode.trim()).orElseThrow(() -> new IllegalArgumentException("Course not found."));
        StudyGroup group = groups.findByNameIgnoreCase(groupName.trim()).orElseThrow(() -> new IllegalArgumentException("Group not found."));
        if (assignments.existsByTeacherIdAndCourseIdAndGroupId(teacher.getId(), course.getId(), group.getId()))
            throw new IllegalArgumentException("That teacher is already assigned to this course and group.");
        assignments.save(new TeacherGroupAssignment(teacher, course, group));
    }
}
