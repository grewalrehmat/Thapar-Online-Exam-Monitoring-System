package com.javalab.web;
import com.javalab.account.UserAccount;
import com.javalab.submissions.*;
import com.javalab.monitoring.MonitoringService;
import com.javalab.account.StudentEnrollmentRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
public class TeacherEvaluationController{
 private final EvaluationService evaluations;
 private final MonitoringService monitoring;
 private final StudentEnrollmentRepository enrollments;
 public TeacherEvaluationController(EvaluationService evaluations,MonitoringService monitoring,StudentEnrollmentRepository enrollments){this.evaluations=evaluations;this.monitoring=monitoring;this.enrollments=enrollments;}
 @GetMapping("/teacher/students/{studentId}/labs/{labId}")
 public String review(@AuthenticationPrincipal UserAccount teacher,@PathVariable Long studentId,@PathVariable Long labId,Model model){
  TeacherStudentReview review=evaluations.review(teacher,studentId,labId);
  model.addAttribute("teacher",teacher);model.addAttribute("review",review);model.addAttribute("student",review.student());
  model.addAttribute("lab",review.lab());model.addAttribute("submission",review.submission());
  model.addAttribute("runs",review.runs());model.addAttribute("gradeHistory",review.gradeHistory());
  model.addAttribute("snapshots",monitoring.forTeacher(teacher,labId,studentId));
  model.addAttribute("enrollment",enrollments.findByStudentIdOrderByCourseCodeAsc(studentId).stream()
   .filter(e->e.getCourse().getId().equals(review.lab().getCourse().getId())).findFirst().orElse(null));
  return "teacher/student-review";
 }
 @PostMapping("/teacher/submissions/{submissionId}/grade")
 public String grade(@AuthenticationPrincipal UserAccount teacher,@PathVariable Long submissionId,@RequestParam Integer score,@RequestParam(required=false) String feedback,RedirectAttributes flash){
  try{Submission submission=evaluations.grade(teacher,submissionId,score,feedback);flash.addFlashAttribute("success","Grade saved.");
   return "redirect:/teacher/students/"+submission.getStudent().getId()+"/labs/"+submission.getLab().getId();
  }catch(IllegalArgumentException|IllegalStateException e){flash.addFlashAttribute("error",e.getMessage());return "redirect:/teacher";}
 }
 @PostMapping("/teacher/submissions/{submissionId}/runs")
 public String run(@AuthenticationPrincipal UserAccount teacher,@PathVariable Long submissionId,@RequestParam(required=false) String standardInput,
                   @RequestParam(required=false) String returnTo,RedirectAttributes flash){
  try{evaluations.runForReview(teacher,submissionId,standardInput);flash.addFlashAttribute("success","Saved code was run.");}
  catch(org.springframework.web.server.ResponseStatusException e){flash.addFlashAttribute("error",e.getReason());}
  catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}
  return "redirect:"+(returnTo==null||!returnTo.startsWith("/teacher/students/")?"/teacher":returnTo);
 }
}
