package com.javalab.web;
import com.javalab.account.UserAccount;
import com.javalab.labs.*;
import com.javalab.submissions.Submission;
import com.javalab.submissions.SubmissionRepository;
import com.javalab.submissions.IntegrityService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller
@RequestMapping("/student/labs")
public class StudentLabPageController{
 private final LabService labs;private final SubmissionRepository submissions;private final IntegrityService integrity;
 public StudentLabPageController(LabService labs,SubmissionRepository submissions,IntegrityService integrity){this.labs=labs;this.submissions=submissions;this.integrity=integrity;}
 @GetMapping("/{labId}") public String workspace(@AuthenticationPrincipal UserAccount student,@PathVariable Long labId,Model model){
  LabSession lab=labs.requireStudentAccess(student,labId);Submission submission=submissions.findByStudentIdAndLabId(student.getId(),labId).orElse(null);
  if(lab.getStatus()!=LabStatus.ACTIVE){model.addAttribute("student",student);model.addAttribute("lab",lab);model.addAttribute("submission",submission);return "student/lab-unavailable";}
  model.addAttribute("student",student);model.addAttribute("lab",lab);model.addAttribute("submission",submission);model.addAttribute("flagCount",integrity.flagCount(student,lab));
  model.addAttribute("editable",lab.getStatus()==LabStatus.ACTIVE&&(submission==null||submission.getStatus()==com.javalab.submissions.SubmissionStatus.DRAFT));
  return "student/lab-workspace";
 }
}
