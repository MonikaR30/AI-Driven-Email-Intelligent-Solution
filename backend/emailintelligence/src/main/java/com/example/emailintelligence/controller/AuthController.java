package com.example.emailintelligence.controller;

import com.example.emailintelligence.DTO.UserResponseDTO;
import com.example.emailintelligence.model.User;
import com.example.emailintelligence.service.UserService;
import com.example.emailintelligence.util.GmailUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
public class AuthController {

    @Autowired
    private UserService service;

    // ✅ INJECT GmailUtil (it's no longer static)
    @Autowired
    private GmailUtil gmailUtil;

    // REGISTER
    @PostMapping("/register")
    public User register(@RequestBody User user) {
        return service.register(user);
    }

    // LOGIN
    @PostMapping("/user-login")
    public ResponseEntity<UserResponseDTO> userLogin(@RequestBody User user, HttpSession session) {

        User u = service.login(user.getEmail(), user.getPassword());

        session.setAttribute("uid", u.getId());

        return ResponseEntity.ok(new UserResponseDTO(u));
    }

    // CONNECT GMAIL BUTTON
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws Exception {

        // ✅ USE INSTANCE METHOD (not static)
        GoogleAuthorizationCodeFlow flow = gmailUtil.getFlow();

        String url = flow.newAuthorizationUrl()
                .setRedirectUri("http://localhost:8080/api/gmail/callback")
                .build();

        response.sendRedirect(url);
    }
}
