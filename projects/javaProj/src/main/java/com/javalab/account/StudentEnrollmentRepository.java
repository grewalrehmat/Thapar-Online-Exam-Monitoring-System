package com.javalab.account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {
    @EntityGraph(attributePaths = {"course", "group"})
    List<StudentEnrollment> findByStudentIdOrderByCourseCodeAsc(Long studentId);
    @EntityGraph(attributePaths = {"student", "course", "group"})
    List<StudentEnrollment> findByCourseIdAndGroupId(Long courseId, Long groupId);
    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);
}
