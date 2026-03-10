package com.example.emailintelligence.controller;

import com.example.emailintelligence.model.Email;
import com.example.emailintelligence.repository.EmailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@RestController
@RequestMapping("/api/email")
@CrossOrigin("*")
public class EmailController {

    @Autowired
    private EmailRepository repo;

    @Autowired
private JavaMailSender mailSender;

@PostMapping("/reply/{id}")
public Map<String, String> sendReply(
        @PathVariable Long id,
        @RequestBody Map<String, String> body) {

    Optional<Email> optEmail = repo.findById(id);
    if (optEmail.isEmpty()) {
        return Map.of("status", "error", "message", "Email not found");
    }
    Email email = optEmail.get();
    String replyText = body.getOrDefault("reply", email.getReply());
    String toAddress = email.getSenderEmail(); // must exist in your Email model

    try {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toAddress);
        message.setSubject("Re: " + email.getSubject());
        message.setText(replyText);
        mailSender.send(message);

        return Map.of("status", "success", "message", "Reply sent to " + toAddress);
    } catch (Exception e) {
        return Map.of("status", "error", "message", e.getMessage());
    }
}

    // AI ANALYZE + SAVE EMAIL
    @PostMapping("/upload")
    public Email upload(@RequestBody Email email){

        email.setDate(LocalDateTime.now()); // 🔥 REQUIRED
        email.setRead(false);
        RestTemplate rt = new RestTemplate();
        Map<String,String> req = Map.of("text", email.getContent());

        Map<String,Object> res =
                rt.postForObject("http://127.0.0.1:5001/analyze", req, Map.class);

        email.setCategory(res.get("category").toString());
        email.setPriority(res.get("priority").toString());
        email.setSummary(res.get("summary").toString());
        email.setReply(res.get("reply").toString());

        return repo.save(email);
    }

   // Dashboard stats
    @GetMapping("/stats/{userId}")
    public Map<String, Long> stats(@PathVariable Long userId) {
        long total = repo.countByUserId(userId);
        long high = repo.countByUserIdAndPriority(userId, "HIGH");
        return Map.of("total", total, "high", high);
    }

    // All inbox
    @GetMapping("/all/{userId}")
public List<Email> all(@PathVariable Long userId) {
    LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
    return repo.findByUserIdAndDateAfterOrderByDateDesc(userId, last30Days);
}


    // High priority
    @GetMapping("/high/{userId}")
public List<Email> high(@PathVariable Long userId) {
    LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
    return repo.findByUserIdAndPriorityOrderByDateDesc(userId, "HIGH")
               .stream()
               .filter(e -> e.getDate().isAfter(last30Days))
               .toList();
}
@GetMapping("/unread/{userId}")
public long unreadCount(@PathVariable Long userId) {
    LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
    return repo.countByUserIdAndIsReadFalseAndDateAfter(userId, last30Days);
}
@PutMapping("/read/{id}")
public void markAsRead(@PathVariable Long id) {
    repo.findById(id).ifPresent(email -> {
        email.setRead(true);
        repo.save(email);
    });
}


    // View single
    @GetMapping("/{id}")
public Email one(@PathVariable Long id) {
    return repo.findById(id).map(email -> {
        email.setRead(true);
        repo.save(email);
        return email;
    }).orElse(null);
}
@GetMapping("/category/{userId}/{category}")
public List<Email> byCategory(
        @PathVariable Long userId,
        @PathVariable String category) {

    LocalDateTime last30Days = LocalDateTime.now().minusDays(30);

    return repo.findByUserIdAndCategoryAndDateAfterOrderByDateDesc(
            userId,
            category,
            last30Days
    );
}

    // Analytics
    @GetMapping("/analytics/{userId}")
    public Map<String, Long> analytics(@PathVariable Long userId) {
        Map<String, Long> map = new HashMap<>();
        for (String cat : List.of("OTP","Job","Meeting","Support","Spam","Personal","Work","Other")) {
            map.put(cat, repo.countByUserIdAndCategory(userId, cat));
        }
        return map;
    }
}
