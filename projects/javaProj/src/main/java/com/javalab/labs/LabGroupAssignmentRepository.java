package com.javalab.labs;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LabGroupAssignmentRepository extends JpaRepository<LabGroupAssignment,Long>{
 @EntityGraph(attributePaths={"lab","lab.course","group"})
 List<LabGroupAssignment> findByGroupIdAndLabCourseIdOrderByLabScheduledDateDesc(Long groupId,Long courseId);
 @EntityGraph(attributePaths={"lab","lab.course","group"})
 List<LabGroupAssignment> findByLabId(Long labId);
 boolean existsByLabIdAndGroupId(Long labId,Long groupId);
}
