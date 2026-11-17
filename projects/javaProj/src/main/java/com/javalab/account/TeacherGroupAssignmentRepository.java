package com.javalab.account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
public interface TeacherGroupAssignmentRepository extends JpaRepository<TeacherGroupAssignment, Long> {
    @EntityGraph(attributePaths = {"course", "group"})
    List<TeacherGroupAssignment> findByTeacherIdOrderByCourseCodeAscGroupNameAsc(Long teacherId);
    boolean existsByTeacherIdAndCourseIdAndGroupId(Long teacherId, Long courseId, Long groupId);
}
