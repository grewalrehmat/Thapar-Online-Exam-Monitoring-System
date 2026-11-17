package com.javalab.submissions;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface GradeRecordRepository extends JpaRepository<GradeRecord,Long>{
 @EntityGraph(attributePaths={"grader"})
 List<GradeRecord> findBySubmissionIdOrderByGradedAtDesc(Long submissionId);
}
