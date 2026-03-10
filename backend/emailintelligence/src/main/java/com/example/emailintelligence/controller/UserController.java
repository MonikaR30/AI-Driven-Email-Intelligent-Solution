package com.example.emailintelligence.controller;



import com.example.emailintelligence.model.User;
import com.example.emailintelligence.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin
public class UserController {

    private final UserRepository repo;

    public UserController(UserRepository repo) {
        this.repo = repo;
    }

    @PostMapping("/register")
    public User register(@RequestBody User user) {
        return repo.save(user);
    }
}
