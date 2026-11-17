package com.javalab.submissions;
import com.javalab.account.*;
import com.javalab.execution.*;
import com.javalab.labs.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class EvaluationService{
 private final LabService labs;private final LabGroupAssignmentRepository targets;private final StudentEnrollmentRepository enrollments;
 private final UserAccountRepository users;private final SubmissionRepository submissions;private final RunRecordRepository runs;
 private final GradeRecordRepository grades;private final JavaRunnerClient runner;private final IntegrityService integrity;
 public EvaluationService(LabService labs,LabGroupAssignmentRepository targets,StudentEnrollmentRepository enrollments,
  UserAccountRepository users,SubmissionRepository submissions,RunRecordRepository runs,GradeRecordRepository grades,JavaRunnerClient runner,IntegrityService integrity){
  this.labs=labs;this.targets=targets;this.enrollments=enrollments;this.users=users;this.submissions=submissions;this.runs=runs;this.grades=grades;this.runner=runner;this.integrity=integrity;
 }
 @Transactional(readOnly=true) public List<TeacherStudentRow> studentsForLab(UserAccount teacher,Long labId){
  LabSession lab=labs.requireTeacherAccess(teacher,labId);Map<Long,TeacherStudentRow> rows=new TreeMap<>();
  Set<Long> teacherGroupIds=teacherAssignmentsFor(teacher,lab.getCourse().getId());
  for(LabGroupAssignment target:targets.findByLabId(labId)){
   if(!teacherGroupIds.contains(target.getGroup().getId()))continue;
   for(StudentEnrollment e:enrollments.findByCourseIdAndGroupId(lab.getCourse().getId(),target.getGroup().getId())){
    Submission submission=submissions.findByStudentIdAndLabId(e.getStudent().getId(),labId).orElse(null);
    rows.put(e.getStudent().getId(),new TeacherStudentRow(e.getStudent(),submission,List.of()));
   }
  }
  Map<Long,List<TeacherActivityView>> activity=new HashMap<>();
  integrity.allForLab(teacher,labId).forEach(event->activity.computeIfAbsent(event.getStudent().getId(),ignored->new ArrayList<>()).add(integrity.present(event)));
  return rows.values().stream().map(row->new TeacherStudentRow(row.student(),row.submission(),activity.getOrDefault(row.student().getId(),List.of())))
   .sorted(Comparator.comparing(r->r.student().getName(),String.CASE_INSENSITIVE_ORDER)).toList();
 }
 @Transactional(readOnly=true) public TeacherStudentReview review(UserAccount teacher,Long studentId,Long labId){
  LabSession lab=labs.requireTeacherAccess(teacher,labId);
  UserAccount student=users.findById(studentId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Student not found."));
  labs.requireTeacherStudentAccess(teacher,labId,studentId);
  Submission submission=submissions.findByStudentIdAndLabId(studentId,labId).orElse(null);
  List<TeacherActivityView> activity=integrity.activityForStudent(teacher,labId,studentId);
  if(submission==null)return new TeacherStudentReview(student,lab,null,List.of(),List.of(),activity);
  return new TeacherStudentReview(student,lab,submission,runs.findTop10BySubmissionIdOrderByCompletedAtDesc(submission.getId()),grades.findBySubmissionIdOrderByGradedAtDesc(submission.getId()),activity);
 }
 @Transactional public Submission grade(UserAccount teacher,Long submissionId,int score,String feedback){
  Submission submission=submissions.findById(submissionId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Submission not found."));
  labs.requireTeacherStudentAccess(teacher,submission.getLab().getId(),submission.getStudent().getId());
  if(score<0||score>100)throw new IllegalArgumentException("Score must be between 0 and 100.");
  submission.grade(teacher,score,feedback);
  grades.save(new GradeRecord(submission,teacher,score,feedback==null?null:feedback.trim()));integrity.record(submission.getStudent(),submission.getLab(),submission,IntegrityEventType.GRADE_UPDATED);return submission;
 }
 @Transactional public RunRecord runForReview(UserAccount teacher,Long submissionId,String standardInput){
  Submission submission=submissions.findById(submissionId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Submission not found."));
  labs.requireTeacherStudentAccess(teacher,submission.getLab().getId(),submission.getStudent().getId());
  if(standardInput!=null&&standardInput.length()>10000)throw new IllegalArgumentException("Program input is too long.");
  String stdin=standardInput==null?"":standardInput;
  RunnerResponse result=runner.run(submission.getSourceCode(),stdin);
  RunRecord run=runs.save(new RunRecord(submission,submission.getSourceCode(),stdin,safe(result.standardOutput()),safe(result.standardError()),result.exitCode(),result.timedOut(),result.durationMs()));
  integrity.record(submission.getStudent(),submission.getLab(),submission,IntegrityEventType.RUN,"teacher-review-run");
  return run;
 }
 private String safe(String value){if(value==null)return "";return value.length()>20000?value.substring(0,20000):value;}
 private Set<Long> teacherAssignmentsFor(UserAccount teacher,Long courseId){
  return labs.teacherGroups(teacher.getId()).stream().filter(a->a.getCourse().getId().equals(courseId))
   .map(a->a.getGroup().getId()).collect(java.util.stream.Collectors.toSet());
 }
}
