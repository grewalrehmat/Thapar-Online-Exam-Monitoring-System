package com.javalab.repository;

import com.javalab.model.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityRepository extends JpaRepository<ActivityEvent, Long> {
    List<ActivityEvent> findTop100ByLabIdOrderByTimestampDesc(Long labId);
    List<ActivityEvent> findByStudentIdAndLabIdOrderByTimestampDesc(Long studentId, Long labId);

    long countByStudentIdAndLabIdAndType(
            Long studentId,
            Long labId,
            com.javalab.model.ActivityType type
    );
}
