package com.javalab.submissions;
import com.javalab.account.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="grade_record",indexes=@Index(name="ix_grade_submission_time",columnList="submission_id, graded_at"))
public class GradeRecord{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="submission_id",nullable=false) private Submission submission;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="graded_by",nullable=false) private UserAccount grader;
 @Column(nullable=false) private int score;
 @Column(length=10000) private String feedback;
 @Column(name="graded_at",nullable=false,updatable=false) private Instant gradedAt=Instant.now();
 protected GradeRecord(){}
 public GradeRecord(Submission submission,UserAccount grader,int score,String feedback){this.submission=submission;this.grader=grader;this.score=score;this.feedback=feedback;}
 public Long getId(){return id;} public Submission getSubmission(){return submission;} public UserAccount getGrader(){return grader;}
 public int getScore(){return score;} public String getFeedback(){return feedback;} public Instant getGradedAt(){return gradedAt;}
}
