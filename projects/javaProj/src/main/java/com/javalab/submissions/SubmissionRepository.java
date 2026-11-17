package com.javalab.submissions;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
public interface SubmissionRepository extends JpaRepository<Submission,Long>{
 @EntityGraph(attributePaths={"student","lab","lab.course","gradedBy"})
 Optional<Submission> findByStudentIdAndLabId(Long studentId,Long labId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select s from Submission s where s.student.id=:studentId and s.lab.id=:labId")
 Optional<Submission> lockByStudentIdAndLabId(@Param("studentId") Long studentId,@Param("labId") Long labId);
 @EntityGraph(attributePaths={"student","lab","lab.course","gradedBy"})
 List<Submission> findByLabIdOrderByStudentNameAsc(Long labId);
 @Override @EntityGraph(attributePaths={"student","lab","lab.course","gradedBy"})
 Optional<Submission> findById(Long id);
}
