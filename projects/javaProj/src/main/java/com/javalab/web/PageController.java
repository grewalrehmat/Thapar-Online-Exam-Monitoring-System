package com.javalab.web;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class PageController {
    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication == null) return "redirect:/login";
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean teacher = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"));
        return admin ? "redirect:/admin" : teacher ? "redirect:/teacher" : "redirect:/student";
    }
    @GetMapping("/login") public String login() { return "login"; }
}
