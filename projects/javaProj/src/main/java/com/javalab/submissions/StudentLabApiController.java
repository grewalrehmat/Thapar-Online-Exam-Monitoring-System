package com.javalab.submissions;
import com.javalab.account.UserAccount;
import com.javalab.execution.*;
import com.javalab.labs.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
@RestController
@RequestMapping("/api/student/labs/{labId}")
public class StudentLabApiController{
 private final LabService labs;private final RunRecordRepository runs;private final JavaRunnerClient runner;private final IntegrityService integrity;
 public StudentLabApiController(LabService labs,RunRecordRepository runs,JavaRunnerClient runner,IntegrityService integrity){this.labs=labs;this.runs=runs;this.runner=runner;this.integrity=integrity;}
 @GetMapping("/status") public StudentLabStatusView status(@AuthenticationPrincipal UserAccount student,@PathVariable Long labId){
  LabSession lab=labs.requireStudentAccess(student,labId);Submission submission=labs.submissionFor(student,labId);
  boolean editable=lab.getStatus()==LabStatus.ACTIVE&&(submission==null||submission.getStatus()==SubmissionStatus.DRAFT);
  return new StudentLabStatusView(lab.getStatus(),editable,lab.isSecureMode(),integrity.flagCount(student,lab));
 }
 @PutMapping("/draft") public SubmissionView saveDraft(@AuthenticationPrincipal UserAccount student,@PathVariable Long labId,@Valid @RequestBody CodeRequest request){
  LabSession lab=labs.requireStudentAccess(student,labId);Submission saved=labs.saveDraft(student,lab,request.sourceCode());integrity.record(student,lab,saved,IntegrityEventType.DRAFT_SAVE);return SubmissionView.from(saved);
 }
 @PostMapping("/runs") public RunResponseView run(@AuthenticationPrincipal UserAccount student,@PathVariable Long labId,@Valid @RequestBody CodeRequest request){
  LabSession lab=labs.requireStudentAccess(student,labId);Submission submission=labs.saveDraft(student,lab,request.sourceCode());
  if(submission.getStatus()!=SubmissionStatus.DRAFT)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"This solution has already been submitted.");
  String stdin=request.standardInput()==null?"":request.standardInput();RunnerResponse result=runner.run(request.sourceCode(),stdin);
  RunRecord saved=runs.save(new RunRecord(submission,request.sourceCode(),stdin,limit(result.standardOutput()),limit(result.standardError()),result.exitCode(),result.timedOut(),result.durationMs()));
  integrity.record(student,lab,submission,IntegrityEventType.RUN);
  return new RunResponseView(saved.getStandardOutput(),saved.getStandardError(),saved.getExitCode(),saved.isTimedOut(),saved.getDurationMs(),Instant.now());
 }
 @PostMapping("/submit") @ResponseStatus(HttpStatus.CREATED)
 public SubmissionView submit(@AuthenticationPrincipal UserAccount student,@PathVariable Long labId,@Valid @RequestBody CodeRequest request){
  LabSession lab=labs.requireStudentAccess(student,labId);Submission before=labs.submissionFor(student,labId);
  boolean wasDraft=before==null||before.getStatus()==SubmissionStatus.DRAFT;
  Submission submission=labs.submit(student,lab,request.sourceCode());
  if(wasDraft&&submission.getStatus()==SubmissionStatus.SUBMITTED)integrity.record(student,lab,submission,IntegrityEventType.SUBMIT);
  return SubmissionView.from(submission);
 }
 @PostMapping("/events") public IntegrityEventView event(@AuthenticationPrincipal UserAccount student,@PathVariable Long labId,@Valid @RequestBody BrowserEventRequest request){
  return integrity.recordBrowserEvent(student,labId,request);
 }
 private String limit(String value){if(value==null)return "";return value.length()>20000?value.substring(0,20000):value;}
}
