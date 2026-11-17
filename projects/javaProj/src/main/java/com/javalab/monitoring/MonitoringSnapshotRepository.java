package com.javalab.monitoring;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface MonitoringSnapshotRepository extends JpaRepository<MonitoringSnapshot, Long> {
    @EntityGraph(attributePaths = {"lab", "student", "submission"})
    List<MonitoringSnapshot> findByLabIdAndStudentIdOrderByCapturedAtDesc(Long labId, Long studentId);
    @EntityGraph(attributePaths = {"lab", "student"})
    Optional<MonitoringSnapshot> findBySnapshotId(String snapshotId);
    long deleteByCapturedAtBefore(Instant cutoff);
}
