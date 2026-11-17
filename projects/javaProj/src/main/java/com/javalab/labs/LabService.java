package com.javalab.labs;
import com.javalab.account.*;
import com.javalab.submissions.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@Service
public class LabService{
 private final LabSessionRepository labs;private final LabGroupAssignmentRepository targets;private final CourseRepository courses;
 private final StudyGroupRepository groups;private final StudentEnrollmentRepository enrollments;
 private final TeacherGroupAssignmentRepository teacherAssignments;private final SubmissionRepository submissions;
 private final IntegrityEventRepository integrityEvents;
 public LabService(LabSessionRepository labs,LabGroupAssignmentRepository targets,CourseRepository courses,StudyGroupRepository groups,
  StudentEnrollmentRepository enrollments,TeacherGroupAssignmentRepository teacherAssignments,SubmissionRepository submissions,IntegrityEventRepository integrityEvents){
  this.labs=labs;this.targets=targets;this.courses=courses;this.groups=groups;this.enrollments=enrollments;this.teacherAssignments=teacherAssignments;this.submissions=submissions;this.integrityEvents=integrityEvents;
 }
 @Transactional public LabSession createLab(UserAccount teacher,String courseCode,String title,String question,LocalDate date,boolean secure,boolean camera,List<String> groupNames){
  Course course=courses.findByCodeIgnoreCase(courseCode.trim()).orElseThrow(()->new IllegalArgumentException("Course not found."));
  if(title==null||title.isBlank()||title.length()>140)throw new IllegalArgumentException("Lab title is required (maximum 140 characters).");
  if(question==null||question.isBlank()||question.length()>12000)throw new IllegalArgumentException("Lab question is required (maximum 12,000 characters).");
  if(date==null)throw new IllegalArgumentException("Lab date is required.");
  if(camera&&!secure)throw new IllegalArgumentException("Camera monitoring is only available for secure labs.");
  if(groupNames==null||groupNames.isEmpty())throw new IllegalArgumentException("Choose at least one group.");
  List<StudyGroup> chosen=new ArrayList<>();
  for(String name:new LinkedHashSet<>(groupNames)){
   StudyGroup group=groups.findByNameIgnoreCase(name.trim()).orElseThrow(()->new IllegalArgumentException("Group not found: "+name));
   if(!teacherAssignments.existsByTeacherIdAndCourseIdAndGroupId(teacher.getId(),course.getId(),group.getId()))
    throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You can only create labs for your assigned groups.");
   chosen.add(group);
  }
  LabSession lab=labs.save(new LabSession(course,title.trim(),question.trim(),date,secure,camera,teacher));
  chosen.forEach(group->targets.save(new LabGroupAssignment(lab,group)));return lab;
 }
 @Transactional(readOnly=true) public List<StudentLabCard> studentLabs(UserAccount student){
  List<StudentLabCard> result=new ArrayList<>();
  for(StudentEnrollment e:enrollments.findByStudentIdOrderByCourseCodeAsc(student.getId()))
   for(LabGroupAssignment t:targets.findByGroupIdAndLabCourseIdOrderByLabScheduledDateDesc(e.getGroup().getId(),e.getCourse().getId()))
    result.add(new StudentLabCard(t.getLab(),e.getCourse(),e.getGroup().getName(),submissions.findByStudentIdAndLabId(student.getId(),t.getLab().getId()).orElse(null)));
  result.sort(Comparator.comparing((StudentLabCard c)->c.lab().getScheduledDate()).reversed());return result;
 }
 @Transactional(readOnly=true) public List<LabSession> teacherLabs(UserAccount teacher){
  Set<Long> ids=new HashSet<>();List<LabSession> result=new ArrayList<>();
  for(TeacherGroupAssignment a:teacherAssignments.findByTeacherIdOrderByCourseCodeAscGroupNameAsc(teacher.getId()))
   for(LabGroupAssignment t:targets.findByGroupIdAndLabCourseIdOrderByLabScheduledDateDesc(a.getGroup().getId(),a.getCourse().getId()))
    if(ids.add(t.getLab().getId()))result.add(t.getLab());
  result.sort(Comparator.comparing(LabSession::getScheduledDate).reversed());return result;
 }
 @Transactional(readOnly=true) public List<StudyGroup> groupsForLab(Long labId){
  return targets.findByLabId(labId).stream().map(LabGroupAssignment::getGroup).toList();
 }
 @Transactional(readOnly=true) public List<StudyGroup> groupsForTeacherLab(UserAccount teacher,Long labId){
  LabSession lab=requireTeacherAccess(teacher,labId);Set<Long> assigned=teacherGroups(teacher.getId()).stream()
   .filter(a->a.getCourse().getId().equals(lab.getCourse().getId())).map(a->a.getGroup().getId()).collect(java.util.stream.Collectors.toSet());
  return targets.findByLabId(labId).stream().map(LabGroupAssignment::getGroup).filter(group->assigned.contains(group.getId())).toList();
 }
 @Transactional(readOnly=true) public List<TeacherGroupAssignment> teacherGroups(Long teacherId){return teacherAssignments.findByTeacherIdOrderByCourseCodeAscGroupNameAsc(teacherId);}
 @Transactional(readOnly=true) public LabSession requireStudentAccess(UserAccount student,Long labId){
  LabSession lab=labs.findById(labId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Lab not found."));
  boolean assigned=enrollments.findByStudentIdOrderByCourseCodeAsc(student.getId()).stream().anyMatch(e->
   e.getCourse().getId().equals(lab.getCourse().getId())&&targets.existsByLabIdAndGroupId(labId,e.getGroup().getId()));
  if(!assigned)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This lab is not assigned to your group.");return lab;
 }
 @Transactional public LabSession requireTeacherAccess(UserAccount teacher,Long labId){
  LabSession lab=labs.findById(labId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Lab not found."));
  boolean allowed=targets.findByLabId(labId).stream().anyMatch(t->teacherAssignments.existsByTeacherIdAndCourseIdAndGroupId(teacher.getId(),lab.getCourse().getId(),t.getGroup().getId()));
  if(!allowed)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This lab is outside your assigned groups.");return lab;
 }
 @Transactional(readOnly=true) public void requireTeacherStudentAccess(UserAccount teacher,Long labId,Long studentId){
  LabSession lab=requireTeacherAccess(teacher,labId);Set<Long> assignedGroups=new HashSet<>();
  teacherAssignments.findByTeacherIdOrderByCourseCodeAscGroupNameAsc(teacher.getId()).stream()
   .filter(a->a.getCourse().getId().equals(lab.getCourse().getId())).map(a->a.getGroup().getId()).forEach(assignedGroups::add);
  boolean allowed=targets.findByLabId(labId).stream().filter(t->assignedGroups.contains(t.getGroup().getId()))
   .anyMatch(t->enrollments.findByCourseIdAndGroupId(lab.getCourse().getId(),t.getGroup().getId()).stream()
    .anyMatch(e->e.getStudent().getId().equals(studentId)));
  if(!allowed)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This student is outside your assigned groups.");
 }
 @Transactional(readOnly=true) public void requireLabCreator(UserAccount teacher,Long labId){
  LabSession lab=requireTeacherAccess(teacher,labId);
  if(!lab.getCreatedBy().getId().equals(teacher.getId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the lab creator can change lab controls.");
 }
 @Transactional public LabSession changeStatus(UserAccount teacher,Long labId,LabStatus status){
  requireLabCreator(teacher,labId);LabSession lab=labs.findById(labId).orElseThrow();
  if(lab.getStatus()==LabStatus.ARCHIVED)throw new IllegalStateException("Archived labs cannot be reopened.");
  if(status==LabStatus.ARCHIVED&&lab.getStatus()==LabStatus.ACTIVE)throw new IllegalStateException("Close the lab before archiving it.");
  lab.setStatus(status);return lab;
 }
 @Transactional public LabSession updateLab(UserAccount teacher,Long labId,String title,String question,LocalDate date,List<String> groupNames){
  requireLabCreator(teacher,labId);LabSession lab=labs.findById(labId).orElseThrow();
  if(lab.getStatus()==LabStatus.ACTIVE||lab.getStatus()==LabStatus.ARCHIVED)throw new IllegalStateException("Close the lab before editing it.");
  if(title==null||title.isBlank()||title.length()>140)throw new IllegalArgumentException("Lab title is required (maximum 140 characters).");
  if(question==null||question.isBlank()||question.length()>12000)throw new IllegalArgumentException("Lab question is required (maximum 12,000 characters).");
  if(date==null)throw new IllegalArgumentException("Lab date is required.");
  if(groupNames==null||groupNames.isEmpty())throw new IllegalArgumentException("Choose at least one group.");
  List<StudyGroup> chosen=new ArrayList<>();
  for(String name:new LinkedHashSet<>(groupNames)){
   StudyGroup group=groups.findByNameIgnoreCase(name.trim()).orElseThrow(()->new IllegalArgumentException("Group not found: "+name));
   if(!teacherAssignments.existsByTeacherIdAndCourseIdAndGroupId(teacher.getId(),lab.getCourse().getId(),group.getId()))
    throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You can only assign your teaching groups.");
   chosen.add(group);
  }
  List<LabGroupAssignment> existing=targets.findByLabId(labId);
  Set<Long> chosenIds=new HashSet<>();chosen.forEach(group->chosenIds.add(group.getId()));
  Set<Long> existingIds=new HashSet<>();
  existing.forEach(target->{existingIds.add(target.getGroup().getId());if(!chosenIds.contains(target.getGroup().getId()))targets.delete(target);});
  chosen.stream().filter(group->!existingIds.contains(group.getId())).forEach(group->targets.save(new LabGroupAssignment(lab,group)));
  lab.updateDetails(title.trim(),question.trim(),date);return lab;
 }
 @Transactional public Submission getOrCreateDraft(UserAccount student,LabSession lab){
  if(lab.getStatus()!=LabStatus.ACTIVE)throw new ResponseStatusException(HttpStatus.CONFLICT,"This lab is not open.");
  return submissions.findByStudentIdAndLabId(student.getId(),lab.getId()).orElseGet(()->submissions.save(new Submission(student,lab,"")));
 }
 @Transactional(readOnly=true) public Submission submissionFor(UserAccount student,Long labId){return submissions.findByStudentIdAndLabId(student.getId(),labId).orElse(null);}
 @Transactional public Submission saveDraft(UserAccount student,LabSession lab,String source){
  requireSecureStart(student,lab);
  Submission s=getOrCreateDraft(student,lab);if(s.getStatus()!=SubmissionStatus.DRAFT)throw new ResponseStatusException(HttpStatus.CONFLICT,"This solution has already been submitted.");
  s.saveDraft(source);return s;
 }
 @Transactional public Submission submit(UserAccount student,LabSession lab,String source){requireSecureStart(student,lab);Submission s=getOrCreateDraft(student,lab);if(s.getStatus()==SubmissionStatus.DRAFT)s.submit(source);return s;}
 private void requireSecureStart(UserAccount student,LabSession lab){
  if(lab.isSecureMode()&&!integrityEvents.existsByStudentIdAndLabIdAndType(student.getId(),lab.getId(),IntegrityEventType.SESSION_STARTED))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Enter full screen to start this secure lab before editing.");
 }
}
