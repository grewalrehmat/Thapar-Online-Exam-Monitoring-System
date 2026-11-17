package com.javalab.submissions;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RunRecordRepository extends JpaRepository<RunRecord,Long>{
 List<RunRecord> findTop10BySubmissionIdOrderByCompletedAtDesc(Long submissionId);
}
