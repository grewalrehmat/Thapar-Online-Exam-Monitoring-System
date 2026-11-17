package com.javalab.web;
import com.javalab.account.*;
import com.javalab.labs.*;
import com.javalab.submissions.EvaluationService;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.List;
@Controller
@RequestMapping("/teacher/labs")
public class TeacherLabController{
 private final LabService labs;private final CourseRepository courses;private final StudyGroupRepository groups;
 private final TeacherGroupAssignmentRepository assignments;private final EvaluationService evaluations;
 public TeacherLabController(LabService labs,CourseRepository courses,StudyGroupRepository groups,TeacherGroupAssignmentRepository assignments,EvaluationService evaluations){
  this.labs=labs;this.courses=courses;this.groups=groups;this.assignments=assignments;this.evaluations=evaluations;
 }
 @GetMapping("/new") public String form(@AuthenticationPrincipal UserAccount teacher,Model model){
  List<TeacherGroupAssignment> assigned=assignments.findByTeacherIdOrderByCourseCodeAscGroupNameAsc(teacher.getId());
  model.addAttribute("teacher",teacher);model.addAttribute("assignments",assigned);
  model.addAttribute("teacherCourses",assigned.stream().map(TeacherGroupAssignment::getCourse).distinct().toList());
  return "teacher/lab-form";
 }
 @PostMapping public String create(@AuthenticationPrincipal UserAccount teacher,@RequestParam String courseCode,@RequestParam String title,
   @RequestParam String question,@RequestParam LocalDate scheduledDate,@RequestParam(defaultValue="false") boolean secureMode,
   @RequestParam(defaultValue="false") boolean cameraMonitoring,@RequestParam(required=false) List<String> groupNames,RedirectAttributes flash){
  try{LabSession lab=labs.createLab(teacher,courseCode,title,question,scheduledDate,secureMode,cameraMonitoring,groupNames);
   flash.addFlashAttribute("success","Lab created.");return "redirect:/teacher/labs/"+lab.getId();
  }catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());return "redirect:/teacher/labs/new";}
 }
 @GetMapping("/{labId}") public String details(@AuthenticationPrincipal UserAccount teacher,@PathVariable Long labId,Model model){
  LabSession lab=labs.requireTeacherAccess(teacher,labId);model.addAttribute("teacher",teacher);model.addAttribute("lab",lab);
  model.addAttribute("canManage",lab.getCreatedBy().getId().equals(teacher.getId()));
  model.addAttribute("targets",labs.groupsForTeacherLab(teacher,labId));model.addAttribute("students",evaluations.studentsForLab(teacher,labId));return "teacher/lab-detail";
 }
 @GetMapping("/{labId}/edit") public String editForm(@AuthenticationPrincipal UserAccount teacher,@PathVariable Long labId,Model model){
  labs.requireLabCreator(teacher,labId);LabSession lab=labs.requireTeacherAccess(teacher,labId);
  List<TeacherGroupAssignment> assigned=assignments.findByTeacherIdOrderByCourseCodeAscGroupNameAsc(teacher.getId()).stream()
   .filter(a->a.getCourse().getId().equals(lab.getCourse().getId())).toList();
  model.addAttribute("teacher",teacher);model.addAttribute("lab",lab);model.addAttribute("assignments",assigned);
  model.addAttribute("selectedGroupNames",labs.groupsForLab(labId).stream().map(StudyGroup::getName).collect(java.util.stream.Collectors.toSet()));
  return "teacher/lab-edit";
 }
 @PostMapping("/{labId}/edit") public String edit(@AuthenticationPrincipal UserAccount teacher,@PathVariable Long labId,
   @RequestParam String title,@RequestParam String question,@RequestParam LocalDate scheduledDate,
   @RequestParam(required=false) List<String> groupNames,RedirectAttributes flash){
  try{labs.updateLab(teacher,labId,title,question,scheduledDate,groupNames);flash.addFlashAttribute("success","Lab changes saved.");}
  catch(IllegalArgumentException|IllegalStateException e){flash.addFlashAttribute("error",e.getMessage());return "redirect:/teacher/labs/"+labId+"/edit";}
  return "redirect:/teacher/labs/"+labId;
 }
 @PostMapping("/{labId}/archive") public String archive(@AuthenticationPrincipal UserAccount teacher,@PathVariable Long labId,RedirectAttributes flash){
  try{labs.changeStatus(teacher,labId,LabStatus.ARCHIVED);flash.addFlashAttribute("success","Lab archived.");}
  catch(IllegalStateException e){flash.addFlashAttribute("error",e.getMessage());}
  return "redirect:/teacher/labs/"+labId;
 }
 @PostMapping("/{labId}/open") public String open(@AuthenticationPrincipal UserAccount teacher,@PathVariable Long labId,RedirectAttributes flash){
  labs.changeStatus(teacher,labId,LabStatus.ACTIVE);flash.addFlashAttribute("success","Lab is open.");return "redirect:/teacher/labs/"+labId;
 }
 @PostMapping("/{labId}/close") public String close(@AuthenticationPrincipal UserAccount teacher,@PathVariable Long labId,RedirectAttributes flash){
  labs.changeStatus(teacher,labId,LabStatus.CLOSED);flash.addFlashAttribute("success","Lab is closed.");return "redirect:/teacher/labs/"+labId;
 }
}
