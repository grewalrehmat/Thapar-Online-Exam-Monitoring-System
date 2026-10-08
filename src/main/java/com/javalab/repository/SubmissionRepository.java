package com.javalab.repository;

import com.javalab.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findByLabIdAndStudentId(Long labId, Long studentId);
    List<Submission> findByLabId(Long labId);
    List<Submission> findByStudentId(Long studentId);
}
