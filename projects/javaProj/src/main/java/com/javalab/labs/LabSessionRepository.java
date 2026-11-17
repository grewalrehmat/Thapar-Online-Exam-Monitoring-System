package com.javalab.labs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
public interface LabSessionRepository extends JpaRepository<LabSession,Long>{
 @Override @EntityGraph(attributePaths={"course"}) Optional<LabSession> findById(Long id);
}
