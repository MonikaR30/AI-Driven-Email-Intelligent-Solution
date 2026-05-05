# AI Email Intelligence System

## 🚀 Overview
A scalable backend system that integrates Gmail API with AI-powered email analysis to automate classification, summarization, and smart reply generation.

Built using a microservices architecture with a Spring Boot backend and a Flask-based AI service.

---

## 🧠 Key Features

- Gmail API integration with OAuth2 authentication  
- AI-powered email analysis (LLM + NLP models)  
- Automatic email classification (OTP, Job, Meeting, etc.)  
- Smart reply generation system  
- Priority detection (HIGH / MEDIUM / LOW)  
- Email caching system to avoid repeated AI calls  
- Asynchronous email synchronization  
- REST API-based backend  

---

## 🏗️ System Architecture

### Backend (Spring Boot)
- REST APIs for email operations  
- User authentication with BCrypt encryption  
- Gmail sync service using Google API  
- Asynchronous processing using `@Async`  
- JPA-based database operations  

### AI Service (Flask)
- Groq LLM integration (Llama model)  
- Zero-shot classification (BART model)  
- Email text cleaning and processing  
- Intelligent categorization + summary + reply generation  

---

## 🔄 Workflow

1. User connects Gmail via OAuth  
2. Backend fetches emails using Gmail API  
3. Emails sent to AI service for analysis  
4. AI returns:
   - category  
   - priority  
   - summary  
   - reply  
5. Backend stores results in database  
6. APIs return processed emails to frontend  

---

## ⚙️ Tech Stack

### Backend
- Java, Spring Boot
- Spring Data JPA
- MySQL / H2
- JavaMailSender

### AI Service
- Python, Flask
- Groq API (LLM)
- HuggingFace Transformers
- BeautifulSoup (HTML cleaning)

---

## 📂 Core Components

- `AuthController` → User login & Gmail OAuth  
- `GmailController` → Gmail connection & callback  
- `EmailController` → Email APIs (analyze, reply, stats)  
- `GmailSyncService` → Async email processing  
- `UserService` → Authentication logic  
- `EmailRepository` → Database queries  

---

## 🔌 Important APIs

### Auth
- POST `/api/auth/register`
- POST `/api/auth/user-login`

### Gmail
- GET `/api/gmail/connect`
- GET `/api/gmail/callback`

### Email
- POST `/api/email/upload`
- GET `/api/email/all/{userId}`
- GET `/api/email/high/{userId}`
- GET `/api/email/analytics/{userId}`
- POST `/api/email/reply/{id}`

---

## ⚡ Performance Optimization

- Implemented caching to store analyzed emails  
- Reduced repeated AI API calls significantly  
- Async processing improves system responsiveness  

---

## 🔐 Security

- Password encryption using BCrypt  
- OAuth2 authentication for Gmail access  
- Environment variables for API keys  

⚠️ Note: API keys should not be committed to GitHub

---

## ▶️ How to Run

### Backend
```bash
cd backend
mvn spring-boot:run
