package com.javalab.submissions;
import com.javalab.account.UserAccount;
import com.javalab.labs.LabSession;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="submission", uniqueConstraints=@UniqueConstraint(name="uk_submission_student_lab", columnNames={"student_id","lab_id"}))
public class Submission {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="student_id",nullable=false) private UserAccount student;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="lab_id",nullable=false) private LabSession lab;
    @Column(name="source_code",nullable=false,length=100000) private String sourceCode;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private SubmissionStatus status=SubmissionStatus.DRAFT;
    @Column(name="last_saved_at",nullable=false) private Instant lastSavedAt=Instant.now();
    @Column(name="submitted_at") private Instant submittedAt;
    @Column(name="score") private Integer score;
    @Column(name="grade_feedback",length=10000) private String feedback;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="graded_by") private UserAccount gradedBy;
    @Column(name="graded_at") private Instant gradedAt;
    @Version @Column(nullable=false) private long version;
    protected Submission() {}
    public Submission(UserAccount student,LabSession lab,String sourceCode){this.student=student;this.lab=lab;this.sourceCode=sourceCode;}
    public Long getId(){return id;} public UserAccount getStudent(){return student;} public LabSession getLab(){return lab;}
    public String getSourceCode(){return sourceCode;} public SubmissionStatus getStatus(){return status;}
    public Instant getLastSavedAt(){return lastSavedAt;} public Instant getSubmittedAt(){return submittedAt;}
    public Integer getScore(){return score;} public String getFeedback(){return feedback;} public UserAccount getGradedBy(){return gradedBy;} public Instant getGradedAt(){return gradedAt;}
    public void saveDraft(String source){this.sourceCode=source;this.lastSavedAt=Instant.now();}
    public void submit(String source){if(status!=SubmissionStatus.DRAFT)throw new IllegalStateException("This solution has already been submitted.");this.sourceCode=source;this.lastSavedAt=Instant.now();this.submittedAt=Instant.now();this.status=SubmissionStatus.SUBMITTED;}
    public void grade(UserAccount grader,int score,String feedback){
        if(score<0||score>100)throw new IllegalArgumentException("Score must be between 0 and 100.");
        if(feedback!=null&&feedback.length()>10000)throw new IllegalArgumentException("Feedback must be 10,000 characters or fewer.");
        this.score=score;this.feedback=feedback==null?null:feedback.trim();this.gradedBy=grader;this.gradedAt=Instant.now();this.status=SubmissionStatus.GRADED;
    }
}
