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

### Data Flow

User → Frontend  
→ Spring Boot Backend (API Layer)  
→ Gmail API (Fetch Emails via OAuth2)  
→ AI Service (Flask)  
→ LLM + NLP Processing  
→ Database (MySQL)  
→ Backend APIs → Frontend  

### Responsibilities

- **Spring Boot Backend**
  - Handles authentication, API routing, and email storage
  - Manages Gmail sync and async processing

- **AI Service (Flask)**
  - Processes email content using LLM + NLP
  - Returns structured output (category, priority, summary, reply)

- **Database**
  - Stores processed emails and metadata
    
## 🏗️ Architecture Diagram

Frontend
   ↓
Spring Boot Backend (Java)
   ↓
Gmail API ←→ OAuth2 Authentication
   ↓
AI Service (Flask)
   ↓
Groq LLM + HuggingFace Model
   ↓
Database (MySQL)

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
## 📡 API Example

### POST /api/email/upload

Request:
{
  "subject": "Meeting at 10 AM",
  "content": "We have a meeting tomorrow at 10 AM",
  "userId": 1
}

Response:
{
  "category": "Meeting",
  "priority": "HIGH",
  "summary": "Meeting scheduled for tomorrow at 10 AM",
  "reply": "I will attend the meeting at 10 AM."
}
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
## 🧠 Design Considerations

- AI processing is separated into a microservice to allow independent scaling  
- Backend avoids blocking calls by using async processing for Gmail sync  
- Caching is used to handle expensive LLM operations efficiently  
- System is designed to handle large inboxes with pagination and filtering

  
## ⚠️ Challenges Faced

- Handling Gmail API pagination and large inboxes  
- Cleaning HTML email content for AI processing  
- Managing API rate limits for LLM calls  
- Ensuring duplicate emails are not reprocessed  

## ▶️ How to Run

### Backend
cd backend
mvn spring-boot:run

### AI Service
cd ai-service
pip install -r requirements.txt
python app.py
