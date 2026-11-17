package com.javalab.submissions;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="run_record",indexes=@Index(name="ix_run_submission_time",columnList="submission_id, completed_at"))
public class RunRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="submission_id",nullable=false) private Submission submission;
    @Column(name="source_code",nullable=false,length=100000) private String sourceCode;
    @Column(name="standard_input",nullable=false,length=10000) private String standardInput;
    @Column(name="standard_output",nullable=false,length=20000) private String standardOutput;
    @Column(name="standard_error",nullable=false,length=20000) private String standardError;
    @Column(name="exit_code") private Integer exitCode;
    @Column(name="timed_out",nullable=false) private boolean timedOut;
    @Column(name="duration_ms",nullable=false) private long durationMs;
    @Column(name="completed_at",nullable=false,updatable=false) private Instant completedAt=Instant.now();
    protected RunRecord() {}
    public RunRecord(Submission submission,String source,String stdin,String stdout,String stderr,Integer exitCode,boolean timedOut,long durationMs){
        this.submission=submission;this.sourceCode=source;this.standardInput=stdin;this.standardOutput=stdout;this.standardError=stderr;this.exitCode=exitCode;this.timedOut=timedOut;this.durationMs=durationMs;
    }
    public Long getId(){return id;} public Submission getSubmission(){return submission;} public String getSourceCode(){return sourceCode;}
    public String getStandardInput(){return standardInput;} public String getStandardOutput(){return standardOutput;}
    public String getStandardError(){return standardError;} public Integer getExitCode(){return exitCode;}
    public boolean isTimedOut(){return timedOut;} public long getDurationMs(){return durationMs;} public Instant getCompletedAt(){return completedAt;}
}
