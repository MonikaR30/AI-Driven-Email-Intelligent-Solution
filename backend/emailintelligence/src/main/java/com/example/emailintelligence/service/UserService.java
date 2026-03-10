package com.example.emailintelligence.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.emailintelligence.model.User;
import com.example.emailintelligence.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository repo;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository repo) {
        this.repo = repo;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public User register(User user) {
        // Check if email already exists
        List<User> existingUsers = repo.findAllByEmail(user.getEmail());
        if (!existingUsers.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, 
                "Email already registered"
            );
        }

        // Validate password length
        if (user.getPassword() == null || user.getPassword().length() < 6) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, 
                "Password must be at least 6 characters"
            );
        }

        // Encrypt password before saving
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        return repo.save(user);
    }

    public User login(String email, String password) {
        List<User> users = repo.findAllByEmail(email);

        if (users.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, 
                "Invalid email or password"
            );
        }

        User dbUser = users.get(0);

        // Compare encrypted password
        if (!passwordEncoder.matches(password, dbUser.getPassword())) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, 
                "Invalid email or password"
            );
        }

        return dbUser;
    }
}