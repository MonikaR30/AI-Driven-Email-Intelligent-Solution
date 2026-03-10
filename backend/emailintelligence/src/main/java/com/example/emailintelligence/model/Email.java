package com.example.emailintelligence.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "emails")
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String subject;         // ✅ Only once
    private String sender;
    private String senderEmail;     // ✅ For sending replies

    private LocalDateTime date;

    @Column(length = 5000)
    private String content;

    private String category;
    private String priority;

    @Column(length = 1000)
    private String reply;

    @Column(length = 2000)
    private String summary;

    @Column(unique = true)
    private String messageId;

    @Column(name = "is_read")
    private boolean isRead = false;

    // ===== GETTERS & SETTERS =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}