package com.nt.controller;

import com.nt.entity.User;
import com.nt.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    @Autowired UserRepository repo;

    @GetMapping({"/", "/login"})
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam String role,
                        jakarta.servlet.http.HttpSession session) {

        java.util.List<User> users = repo.findByUsernameAndPassword(username, password);
        if (users.isEmpty()) users = repo.findByEmailAndPassword(username, password);

        if (!users.isEmpty()) {
            User user = users.stream()
                    .filter(u -> role.equalsIgnoreCase(u.getRole()))
                    .findFirst().orElse(users.get(0));

            if (role.equalsIgnoreCase(user.getRole())) {
                session.setAttribute("currentUserId",       user.getId());
                session.setAttribute("currentUserName",     user.getName());
                session.setAttribute("currentUserUsername", user.getUsername());
                session.setAttribute("currentUserEmail",    user.getEmail());
                session.setAttribute("currentUserRole",     user.getRole());
                session.setAttribute("currentDepartment",   user.getDepartment());

                Authentication auth = new UsernamePasswordAuthenticationToken(
                        user.getUsername(), null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(auth);
                session.setAttribute("SPRING_SECURITY_CONTEXT", ctx);

                if ("ADMIN".equalsIgnoreCase(role))   return "redirect:/admin";
                if ("HOD".equalsIgnoreCase(role))     return "redirect:/hod";
                if ("FACULTY".equalsIgnoreCase(role)) {
                    session.setAttribute("currentFacultyKey",
                            user.getName() != null ? user.getName() : user.getUsername());
                    return "redirect:/faculty-dashboard";
                }
            }
        }

        return "redirect:/login?error";
    }
}
