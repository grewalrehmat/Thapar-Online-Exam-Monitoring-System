package com.javalab.repository;

import com.javalab.model.LabSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LabRepository extends JpaRepository<LabSession, Long> {
    List<LabSession> findAllByOrderByStartsAtDesc();
}
