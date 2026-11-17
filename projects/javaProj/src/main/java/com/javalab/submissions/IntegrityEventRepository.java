package com.javalab.submissions;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IntegrityEventRepository extends JpaRepository<IntegrityEvent, Long> {
    Optional<IntegrityEvent> findByStudentIdAndLabIdAndIdempotencyKey(Long studentId, Long labId, String key);
    boolean existsByStudentIdAndLabIdAndType(Long studentId, Long labId, IntegrityEventType type);
    long countBySubmissionIdAndTypeIn(Long submissionId, Collection<IntegrityEventType> types);
    long countByStudentIdAndLabIdAndTypeIn(Long studentId, Long labId, Collection<IntegrityEventType> types);
    long countBySubmissionIdAndFlagNumberIsNotNull(Long submissionId);
    long countByStudentIdAndLabIdAndFlagNumberIsNotNull(Long studentId, Long labId);
    long countBySubmissionIdAndTypeInAndFlagNumberIsNotNull(Long submissionId, Collection<IntegrityEventType> types);
    long countByStudentIdAndLabIdAndTypeInAndFlagNumberIsNotNull(Long studentId, Long labId, Collection<IntegrityEventType> types);
    Optional<IntegrityEvent> findTopByStudentIdAndLabIdAndTypeInOrderByOccurredAtDesc(Long studentId, Long labId, Collection<IntegrityEventType> types);
    @EntityGraph(attributePaths = {"student", "submission"})
    List<IntegrityEvent> findByLabIdOrderByOccurredAtAscIdAsc(Long labId);
    @EntityGraph(attributePaths = {"student", "submission"})
    List<IntegrityEvent> findByLabIdAndStudentIdOrderByOccurredAtAscIdAsc(Long labId, Long studentId);
}
