package com.javalab.account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {
    Optional<StudyGroup> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
