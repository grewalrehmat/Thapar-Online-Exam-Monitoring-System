package com.javalab.web;
import com.javalab.account.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
@RequestMapping("/admin")
public class AdminController {
    private final AccountAdminService service;
    private final UserAccountRepository users;
    private final CourseRepository courses;
    private final StudyGroupRepository groups;
    public AdminController(AccountAdminService service, UserAccountRepository users, CourseRepository courses, StudyGroupRepository groups) {
        this.service = service; this.users = users; this.courses = courses; this.groups = groups;
    }
    @GetMapping public String dashboard(Model model) {
        model.addAttribute("users", users.findAll(Sort.by("name")));
        model.addAttribute("courses", courses.findAll(Sort.by("code")));
        model.addAttribute("groups", groups.findAll(Sort.by("name")));
        return "admin/dashboard";
    }
    @PostMapping("/users")
    public String createUser(@RequestParam String name, @RequestParam String email, @RequestParam String password,
                             @RequestParam Role role, @RequestParam(required=false) String rollNumber, RedirectAttributes flash) {
        return handle(flash, () -> service.createUser(name, email, password, role, rollNumber), "Account created.");
    }
    @PostMapping("/courses")
    public String createCourse(@RequestParam String code, @RequestParam String name, RedirectAttributes flash) {
        return handle(flash, () -> service.createCourse(code, name), "Course created.");
    }
    @PostMapping("/groups")
    public String createGroup(@RequestParam String name, RedirectAttributes flash) {
        return handle(flash, () -> service.createGroup(name), "Group created.");
    }
    @PostMapping("/assignments/students")
    public String assignStudent(@RequestParam Long userId, @RequestParam String courseCode, @RequestParam String groupName, RedirectAttributes flash) {
        return handle(flash, () -> service.assignStudent(userId, courseCode, groupName), "Student assigned.");
    }
    @PostMapping("/assignments/teachers")
    public String assignTeacher(@RequestParam Long userId, @RequestParam String courseCode, @RequestParam String groupName, RedirectAttributes flash) {
        return handle(flash, () -> service.assignTeacher(userId, courseCode, groupName), "Teacher assigned.");
    }
    private String handle(RedirectAttributes flash, Runnable action, String success) {
        try { action.run(); flash.addFlashAttribute("success", success); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin";
    }
}
