package com.javalab.labs;
import com.javalab.account.*;
import com.javalab.submissions.*;
import com.javalab.execution.JavaRunnerClient;
import com.javalab.execution.RunnerResponse;
import com.javalab.monitoring.MonitoringService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:labs-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "javalab.demo.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class LabWorkflowIntegrationTest {
 @Autowired AccountAdminService accounts;
 @Autowired LabService labs;
 @Autowired SubmissionRepository submissions;
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper objectMapper;
 @Autowired RunRecordRepository runRecords;
 @Autowired IntegrityService integrity;
 @Autowired IntegrityEventRepository integrityEvents;
 @Autowired EvaluationService evaluations;
 @Autowired MonitoringService monitoring;
 @MockBean JavaRunnerClient runner;

 @Test void studentSeesOnlyAssignedLabAndCanResumeSavedDraft() {
  Course course=accounts.createCourse("UCS645","Java");
  StudyGroup group=accounts.createGroup("3C21");
  UserAccount teacher=accounts.createUser("Teacher","t@example.edu","teacher-test-password",Role.TEACHER,null);
  UserAccount student=accounts.createUser("Student","s@example.edu","student-test-password",Role.STUDENT,"R1");
  accounts.assignTeacher(teacher.getId(),course.getCode(),group.getName());
  accounts.assignStudent(student.getId(),course.getCode(),group.getName());
  LabSession lab=labs.createLab(teacher,course.getCode(),"Lab 1","Write a program",LocalDate.now(),false,false,List.of(group.getName()));
  assertThat(labs.studentLabs(student)).extracting(card->card.lab().getId()).containsExactly(lab.getId());
  assertThatThrownBy(()->labs.getOrCreateDraft(student,lab)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  labs.changeStatus(teacher,lab.getId(),LabStatus.ACTIVE);
  Submission saved=labs.saveDraft(student,lab,"public class Main {} ");
  assertThat(labs.getOrCreateDraft(student,lab).getSourceCode()).isEqualTo("public class Main {} ");
  assertThat(submissions.findByStudentIdAndLabId(student.getId(),lab.getId())).contains(saved);
 }

 @Test void submittedSolutionCannotBeChangedByLaterDraftSave() {
  Course course=accounts.createCourse("UCS710","Programming II");
  StudyGroup group=accounts.createGroup("4C21");
  UserAccount teacher=accounts.createUser("Teacher Two","t2@example.edu","teacher-test-password",Role.TEACHER,null);
  UserAccount student=accounts.createUser("Student Two","s2@example.edu","student-test-password",Role.STUDENT,"R2");
  accounts.assignTeacher(teacher.getId(),course.getCode(),group.getName());
  accounts.assignStudent(student.getId(),course.getCode(),group.getName());
  LabSession lab=labs.createLab(teacher,course.getCode(),"Lab 2","Write a program",LocalDate.now(),false,false,List.of(group.getName()));
  labs.changeStatus(teacher,lab.getId(),LabStatus.ACTIVE);
  labs.saveDraft(student,lab,"draft");
  Submission submitted=labs.submit(student,lab,"final source");
  assertThat(submitted.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
  assertThatThrownBy(()->labs.saveDraft(student,lab,"stale autosave"))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(submissions.findByStudentIdAndLabId(student.getId(),lab.getId()).orElseThrow().getSourceCode()).isEqualTo("final source");
 }

 @Test void studentAndTeacherPagesRenderAnAssignedLab() throws Exception {
  Course course=accounts.createCourse("UCS910","Advanced Java");
  StudyGroup group=accounts.createGroup("5C21");
  UserAccount teacher=accounts.createUser("Teacher Web","webteacher@example.edu","teacher-web-password",Role.TEACHER,null);
  UserAccount student=accounts.createUser("Student Web","webstudent@example.edu","student-web-password",Role.STUDENT,"WEB-1");
  accounts.assignTeacher(teacher.getId(),course.getCode(),group.getName());
  accounts.assignStudent(student.getId(),course.getCode(),group.getName());
  LabSession lab=labs.createLab(teacher,course.getCode(),"Web Lab","Print a greeting",LocalDate.now(),false,false,List.of(group.getName()));
  labs.changeStatus(teacher,lab.getId(),LabStatus.ACTIVE);

  MvcResult studentLogin=mvc.perform(post("/login").with(csrf()).param("username",student.getEmail()).param("password","student-web-password"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/student")).andReturn();
  MockHttpSession studentSession=(MockHttpSession)studentLogin.getRequest().getSession(false);
  mvc.perform(get("/student").session(studentSession)).andExpect(status().isOk()).andExpect(view().name("student/dashboard"));
  mvc.perform(get("/student/labs/"+lab.getId()).session(studentSession)).andExpect(status().isOk()).andExpect(view().name("student/lab-workspace"));

  MvcResult teacherLogin=mvc.perform(post("/login").with(csrf()).param("username",teacher.getEmail()).param("password","teacher-web-password"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/teacher")).andReturn();
  MockHttpSession teacherSession=(MockHttpSession)teacherLogin.getRequest().getSession(false);
  mvc.perform(get("/teacher").session(teacherSession)).andExpect(status().isOk()).andExpect(view().name("teacher/dashboard"));
  mvc.perform(get("/teacher/labs/new").session(teacherSession)).andExpect(status().isOk()).andExpect(view().name("teacher/lab-form"));
  mvc.perform(get("/teacher/labs/"+lab.getId()).session(teacherSession)).andExpect(status().isOk()).andExpect(view().name("teacher/lab-detail"));
 }

 @Test void upcomingLabQuestionIsNotReturnedBeforeTeacherOpensIt() throws Exception {
  Course course=accounts.createCourse("UCS970","Private Lab");StudyGroup group=accounts.createGroup("11C21");
  UserAccount teacher=accounts.createUser("Upcoming Teacher","upcoming-teacher@example.edu","upcoming-teacher-password",Role.TEACHER,null);
  UserAccount student=accounts.createUser("Upcoming Student","upcoming-student@example.edu","upcoming-student-password",Role.STUDENT,"UP-1");
  accounts.assignTeacher(teacher.getId(),course.getCode(),group.getName());accounts.assignStudent(student.getId(),course.getCode(),group.getName());
  LabSession lab=labs.createLab(teacher,course.getCode(),"Upcoming Lab","EXAM SECRET QUESTION",LocalDate.now(),true,false,List.of(group.getName()));
  MvcResult login=mvc.perform(post("/login").with(csrf()).param("username",student.getEmail()).param("password","upcoming-student-password"))
   .andExpect(status().is3xxRedirection()).andReturn();
  mvc.perform(get("/student/labs/"+lab.getId()).session((MockHttpSession)login.getRequest().getSession(false)))
   .andExpect(status().isOk()).andExpect(view().name("student/lab-unavailable"))
   .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("EXAM SECRET QUESTION"))));
 }


 @Test void teacherCanEditGroupTargetsAndArchiveLab() {
  Course course=accounts.createCourse("UCS920","Object Oriented Programming");
  StudyGroup first=accounts.createGroup("6C21");
  StudyGroup second=accounts.createGroup("6C22");
  UserAccount teacher=accounts.createUser("Teacher Edit","editteacher@example.edu","teacher-edit-password",Role.TEACHER,null);
  accounts.assignTeacher(teacher.getId(),course.getCode(),first.getName());
  accounts.assignTeacher(teacher.getId(),course.getCode(),second.getName());
  LabSession lab=labs.createLab(teacher,course.getCode(),"Old title","Old question",LocalDate.now(),false,false,List.of(first.getName()));
  labs.updateLab(teacher,lab.getId(),"New title","New question",LocalDate.now().plusDays(1),List.of(second.getName()));
  assertThat(lab.getTitle()).isEqualTo("New title");
  assertThat(labs.groupsForLab(lab.getId())).extracting(StudyGroup::getName).containsExactly(second.getName());
  labs.changeStatus(teacher,lab.getId(),LabStatus.ARCHIVED);
  assertThat(lab.getStatus()).isEqualTo(LabStatus.ARCHIVED);
 }


 @Test void studentCanAutosaveRunAndSubmitThroughApi() throws Exception {
  Course course=accounts.createCourse("UCS930","Algorithms with Java");
  StudyGroup group=accounts.createGroup("7C21");
  UserAccount teacher=accounts.createUser("Teacher API","apiteacher@example.edu","teacher-api-password",Role.TEACHER,null);
  UserAccount student=accounts.createUser("Student API","apistudent@example.edu","student-api-password",Role.STUDENT,"API-1");
  accounts.assignTeacher(teacher.getId(),course.getCode(),group.getName());accounts.assignStudent(student.getId(),course.getCode(),group.getName());
  LabSession lab=labs.createLab(teacher,course.getCode(),"API Lab","Print a value",LocalDate.now(),false,false,List.of(group.getName()));
  labs.changeStatus(teacher,lab.getId(),LabStatus.ACTIVE);
  MvcResult login=mvc.perform(post("/login").with(csrf()).param("username",student.getEmail()).param("password","student-api-password"))
   .andExpect(status().is3xxRedirection()).andReturn();
  MockHttpSession session=(MockHttpSession)login.getRequest().getSession(false);
  String source="public class Main { public static void main(String[] args) { System.out.println(42); } }";
  String payload=objectMapper.writeValueAsString(new CodeRequest(source,""));
  mvc.perform(put("/api/student/labs/"+lab.getId()+"/draft").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
  when(runner.run(source,"")).thenReturn(new RunnerResponse("42","",0,false,95));
  mvc.perform(post("/api/student/labs/"+lab.getId()+"/runs").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
   .andExpect(status().isOk()).andExpect(jsonPath("$.standardOutput").value("42")).andExpect(jsonPath("$.exitCode").value(0));
  assertThat(runRecords.count()).isEqualTo(1);
  mvc.perform(post("/api/student/labs/"+lab.getId()+"/submit").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUBMITTED"));
  mvc.perform(put("/api/student/labs/"+lab.getId()+"/draft").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
   .andExpect(status().isConflict());
 }

 @Test void secureLabCountsCombinedViolationsAndAutoSubmitsOnFourthUniqueEvent() {
  Course course=accounts.createCourse("UCS940","Secure Java");StudyGroup group=accounts.createGroup("8C21");
  UserAccount teacher=accounts.createUser("Secure Teacher","secure-teacher@example.edu","secure-teacher-password",Role.TEACHER,null);
  UserAccount student=accounts.createUser("Secure Student","secure-student@example.edu","secure-student-password",Role.STUDENT,"SEC-1");
  accounts.assignTeacher(teacher.getId(),course.getCode(),group.getName());accounts.assignStudent(student.getId(),course.getCode(),group.getName());
  LabSession lab=labs.createLab(teacher,course.getCode(),"Secure Lab","Write Java",LocalDate.now(),true,false,List.of(group.getName()));
  labs.changeStatus(teacher,lab.getId(),LabStatus.ACTIVE);
  assertThatThrownBy(()->labs.saveDraft(student,lab,"not started"))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  integrity.recordBrowserEvent(student,lab.getId(),new BrowserEventRequest("SESSION_STARTED","00000000-0000-0000-0000-000000000099"));
  labs.saveDraft(student,lab,"public class Main { /* latest saved code */ }");
  assertThat(integrity.recordBrowserEvent(student,lab.getId(),new BrowserEventRequest("COPY","00000000-0000-0000-0000-000000000000")).flagNumber()).isNull();
  BrowserEventRequest first=new BrowserEventRequest("TAB_SWITCH","00000000-0000-0000-0000-000000000001");
  assertThat(integrity.recordBrowserEvent(student,lab.getId(),first).flagNumber()).isEqualTo(1);
  assertThat(integrity.recordBrowserEvent(student,lab.getId(),first).flagNumber()).isEqualTo(1);
  assertThat(integrity.recordBrowserEvent(student,lab.getId(),new BrowserEventRequest("FULLSCREEN_EXIT","00000000-0000-0000-0000-000000000002")).flagNumber()).isEqualTo(2);
  assertThat(integrity.recordBrowserEvent(student,lab.getId(),new BrowserEventRequest("TAB_SWITCH","00000000-0000-0000-0000-000000000003")).flagNumber()).isEqualTo(3);
  IntegrityEventView fourth=integrity.recordBrowserEvent(student,lab.getId(),new BrowserEventRequest("FULLSCREEN_EXIT","00000000-0000-0000-0000-000000000004"));
  assertThat(fourth.autoSubmitted()).isTrue();
  assertThat(integrity.flagCount(student,lab)).isEqualTo(4);
  assertThat(integrity.recordBrowserEvent(student,lab.getId(),new BrowserEventRequest("TAB_SWITCH","00000000-0000-0000-0000-000000000005")).flagNumber()).isNull();
  assertThat(integrity.flagCount(student,lab)).isEqualTo(4);
  Submission submission=submissions.findByStudentIdAndLabId(student.getId(),lab.getId()).orElseThrow();
  assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
  assertThat(submission.getSourceCode()).contains("latest saved code");
  assertThat(integrityEvents.findByLabIdAndStudentIdOrderByOccurredAtAscIdAsc(lab.getId(),student.getId()))
   .extracting(IntegrityEvent::getType).contains(IntegrityEventType.AUTO_SUBMIT);
 }

 @Test void teacherGradeIsRecordedAndVisibleOnStudentSubmission() throws Exception {
  Course course=accounts.createCourse("UCS950","Grading Java");StudyGroup group=accounts.createGroup("9C21");
  UserAccount teacher=accounts.createUser("Grading Teacher","grading-teacher@example.edu","grading-teacher-password",Role.TEACHER,null);
  UserAccount student=accounts.createUser("Grading Student","grading-student@example.edu","grading-student-password",Role.STUDENT,"GRADE-1");
  accounts.assignTeacher(teacher.getId(),course.getCode(),group.getName());accounts.assignStudent(student.getId(),course.getCode(),group.getName());
  LabSession lab=labs.createLab(teacher,course.getCode(),"Grading Lab","Write Java",LocalDate.now(),false,false,List.of(group.getName()));
  labs.changeStatus(teacher,lab.getId(),LabStatus.ACTIVE);Submission submitted=labs.submit(student,lab,"class Main {}");
  evaluations.grade(teacher,submitted.getId(),87,"Good work.");
  Submission graded=submissions.findByStudentIdAndLabId(student.getId(),lab.getId()).orElseThrow();
  assertThat(graded.getStatus()).isEqualTo(SubmissionStatus.GRADED);
  assertThat(graded.getScore()).isEqualTo(87);assertThat(graded.getFeedback()).isEqualTo("Good work.");
  assertThat(integrityEvents.findByLabIdAndStudentIdOrderByOccurredAtAscIdAsc(lab.getId(),student.getId()))
   .extracting(IntegrityEvent::getType).contains(IntegrityEventType.GRADE_UPDATED);
  MvcResult teacherLogin=mvc.perform(post("/login").with(csrf()).param("username",teacher.getEmail()).param("password","grading-teacher-password"))
   .andExpect(status().is3xxRedirection()).andReturn();
  MockHttpSession teacherSession=(MockHttpSession)teacherLogin.getRequest().getSession(false);
  mvc.perform(get("/teacher/students/"+student.getId()+"/labs/"+lab.getId()).session(teacherSession))
   .andExpect(status().isOk()).andExpect(view().name("teacher/student-review"))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("87 / 100")));
  MvcResult studentLogin=mvc.perform(post("/login").with(csrf()).param("username",student.getEmail()).param("password","grading-student-password"))
   .andExpect(status().is3xxRedirection()).andReturn();
  MockHttpSession studentSession=(MockHttpSession)studentLogin.getRequest().getSession(false);
  mvc.perform(get("/student").session(studentSession)).andExpect(status().isOk())
   .andExpect(content().string(org.hamcrest.Matchers.containsString("87 / 100")));
  mvc.perform(get("/student/labs/"+lab.getId()).session(studentSession)).andExpect(status().isOk())
   .andExpect(content().string(org.hamcrest.Matchers.containsString("Good work.")));
  assertThatThrownBy(()->evaluations.grade(accounts.createUser("Other Teacher","other-grader@example.edu","other-grader-password",Role.TEACHER,null),submitted.getId(),90,""))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }

 @Test void teacherRosterAndReviewStayInsideAssignedGroup() {
  Course course=accounts.createCourse("UCS960","Group Scoped Java");StudyGroup first=accounts.createGroup("10C21");StudyGroup second=accounts.createGroup("10C22");
  UserAccount owner=accounts.createUser("Lab Owner","lab-owner@example.edu","lab-owner-password",Role.TEACHER,null);
  UserAccount secondTeacher=accounts.createUser("Second Teacher","second-teacher@example.edu","second-teacher-password",Role.TEACHER,null);
  UserAccount firstStudent=accounts.createUser("First Student","first-student@example.edu","first-student-password",Role.STUDENT,"G-1");
  UserAccount secondStudent=accounts.createUser("Second Student","second-student@example.edu","second-student-password",Role.STUDENT,"G-2");
  accounts.assignTeacher(owner.getId(),course.getCode(),first.getName());accounts.assignTeacher(owner.getId(),course.getCode(),second.getName());
  accounts.assignTeacher(secondTeacher.getId(),course.getCode(),second.getName());
  accounts.assignStudent(firstStudent.getId(),course.getCode(),first.getName());accounts.assignStudent(secondStudent.getId(),course.getCode(),second.getName());
  LabSession lab=labs.createLab(owner,course.getCode(),"Shared Lab","Write Java",LocalDate.now(),false,false,List.of(first.getName(),second.getName()));
  assertThat(evaluations.studentsForLab(secondTeacher,lab.getId())).extracting(row->row.student().getId()).containsExactly(secondStudent.getId());
  assertThatThrownBy(()->evaluations.review(secondTeacher,firstStudent.getId(),lab.getId()))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->labs.changeStatus(secondTeacher,lab.getId(),LabStatus.ACTIVE))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }

 @Test void teacherCanManuallyGradeSavedDraftAndItBecomesReadOnly() {
  Course course=accounts.createCourse("UCS980","Draft Grading");StudyGroup group=accounts.createGroup("12C21");
  UserAccount teacher=accounts.createUser("Draft Grader","draft-grader@example.edu","draft-grader-password",Role.TEACHER,null);
  UserAccount student=accounts.createUser("Draft Author","draft-author@example.edu","draft-author-password",Role.STUDENT,"D-1");
  accounts.assignTeacher(teacher.getId(),course.getCode(),group.getName());accounts.assignStudent(student.getId(),course.getCode(),group.getName());
  LabSession lab=labs.createLab(teacher,course.getCode(),"Draft Lab","Write Java",LocalDate.now(),false,false,List.of(group.getName()));
  labs.changeStatus(teacher,lab.getId(),LabStatus.ACTIVE);Submission draft=labs.saveDraft(student,lab,"class Main { /* saved draft */ }");
  evaluations.grade(teacher,draft.getId(),73,"Review this draft.");
  assertThat(draft.getStatus()).isEqualTo(SubmissionStatus.GRADED);
  assertThat(draft.getScore()).isEqualTo(73);
  assertThatThrownBy(()->labs.saveDraft(student,lab,"late edits"))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }

 @Test void cameraFailureIsRecordedForTeacherWithoutClaimingMonitoringSucceeded() {
  Course course=accounts.createCourse("UCS990","Camera Check");StudyGroup group=accounts.createGroup("13C21");
  UserAccount teacher=accounts.createUser("Camera Teacher","camera-teacher@example.edu","camera-teacher-password",Role.TEACHER,null);
  UserAccount student=accounts.createUser("Camera Student","camera-student@example.edu","camera-student-password",Role.STUDENT,"C-1");
  accounts.assignTeacher(teacher.getId(),course.getCode(),group.getName());accounts.assignStudent(student.getId(),course.getCode(),group.getName());
  LabSession lab=labs.createLab(teacher,course.getCode(),"Camera Lab","Write Java",LocalDate.now(),true,true,List.of(group.getName()));
  labs.changeStatus(teacher,lab.getId(),LabStatus.ACTIVE);
  integrity.recordBrowserEvent(student,lab.getId(),new BrowserEventRequest("SESSION_STARTED","00000000-0000-0000-0000-000000000099"));
  monitoring.reportUnavailable(student,lab.getId(),"permission-denied");
  IntegrityEvent unavailable=integrityEvents.findByLabIdAndStudentIdOrderByOccurredAtAscIdAsc(lab.getId(),student.getId()).stream()
   .filter(event->event.getType()==IntegrityEventType.CAMERA_UNAVAILABLE).findFirst().orElseThrow();
  assertThat(unavailable.getDetails()).isEqualTo("permission-denied");
  assertThat(monitoring.forTeacher(teacher,lab.getId(),student.getId())).isEmpty();
 }

}
