package com.example.emailintelligence.service;

import com.example.emailintelligence.model.Email;
import com.example.emailintelligence.repository.EmailRepository;
import com.example.emailintelligence.util.GmailUtil;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GmailSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GmailSyncService.class);

    @Autowired
    private EmailRepository repo;

    @Autowired
    private GmailUtil gmailUtil;

    private final RestTemplate rt = new RestTemplate();
    private static final Set<Long> syncing = Collections.synchronizedSet(new HashSet<>());

    @Async
    public void sync(Long uid) {
        if (syncing.contains(uid)) {
            logger.info("Sync already in progress for user: {}", uid);
            return;
        }

        syncing.add(uid);
        logger.info("Starting email sync for user: {}", uid);

        try {
            Gmail gmail = gmailUtil.getGmailForUser(uid.toString());

            long thirtyDaysAgoMillis = Instant.now()
                    .minus(30, java.time.temporal.ChronoUnit.DAYS)
                    .toEpochMilli();

            long afterSeconds = thirtyDaysAgoMillis / 1000;

            String nextPageToken = null;
            int totalProcessed = 0;

            do {
                ListMessagesResponse res = gmail.users().messages()
                        .list("me")
                        .setQ("in:inbox after:" + afterSeconds)
                        .setMaxResults(500L)
                        .setPageToken(nextPageToken)
                        .execute();

                if (res.getMessages() != null) {
                    logger.info("Processing {} messages for user: {}", res.getMessages().size(), uid);

                    for (Message m : res.getMessages()) {
                        try {
                            save(gmail, m.getId(), uid, thirtyDaysAgoMillis);
                            totalProcessed++;
                        } catch (Exception e) {
                            logger.error("Error processing message {} for user {}: {}",
                                m.getId(), uid, e.getMessage());
                        }
                    }
                }

                nextPageToken = res.getNextPageToken();
            } while (nextPageToken != null);

            logger.info("Sync completed for user: {}. Processed {} emails.", uid, totalProcessed);

        } catch (Exception e) {
            logger.error("Sync failed for user {}: {}", uid, e.getMessage(), e);
        } finally {
            syncing.remove(uid);
        }
    }

    private void save(Gmail gmail, String messageId, Long uid, long thirtyDaysAgoMillis) throws Exception {

        boolean alreadyExists = repo.existsByMessageIdAndUserId(messageId, uid);

        if (alreadyExists) {
            Email existing = repo.findByMessageIdAndUserId(messageId, uid);

            boolean hasBadSummary = existing != null && existing.getSummary() != null &&
                (existing.getSummary().equals("Analysis unavailable") ||
                 existing.getSummary().contains("unavailable") ||
                 existing.getSummary().contains("Gemini") ||
                 existing.getSummary().contains("quota") ||
                 existing.getSummary().contains("AI unavailable") ||
                 existing.getSummary().contains("No action needed") && existing.getSummary().contains("API key"));

            if (!hasBadSummary) {
                // ✅ Backfill senderEmail if missing even when skipping re-analysis
                if (existing != null &&
                    (existing.getSenderEmail() == null || existing.getSenderEmail().isBlank())) {

                    Message msg = gmail.users().messages()
                            .get("me", messageId)
                            .setFormat("metadata")
                            .setMetadataHeaders(List.of("From"))
                            .execute();

                    String from = "";
                    if (msg.getPayload() != null && msg.getPayload().getHeaders() != null) {
                        for (MessagePartHeader h : msg.getPayload().getHeaders()) {
                            if ("From".equalsIgnoreCase(h.getName())) {
                                from = h.getValue();
                                break;
                            }
                        }
                    }
                    existing.setSenderEmail(extractEmail(from));
                    repo.save(existing);
                    logger.info("Backfilled senderEmail for: {}", messageId);
                }

                logger.debug("Skipping duplicate messageId: {}", messageId);
                return;
            }

            logger.info("Re-analyzing email with bad summary: {}", messageId);
        }

        Message msg = gmail.users().messages()
                .get("me", messageId)
                .setFormat("full")
                .execute();

        if (msg.getInternalDate() == null || msg.getInternalDate() < thirtyDaysAgoMillis) {
            logger.debug("Skipping old email: {}", messageId);
            return;
        }

        String subject    = "";
        String from       = "";
        String dateHeader = "";

        if (msg.getPayload() != null && msg.getPayload().getHeaders() != null) {
            for (MessagePartHeader h : msg.getPayload().getHeaders()) {
                String name  = h.getName();
                String value = h.getValue();

                if (name == null || value == null) continue;

                if (name.equalsIgnoreCase("Subject"))      subject    = value;
                else if (name.equalsIgnoreCase("From"))    from       = value;
                else if (name.equalsIgnoreCase("Date"))    dateHeader = value;
            }
        }

        if (subject == null || subject.isBlank()) {
            logger.debug("Skipping email without subject: {}", messageId);
            return;
        }

        // Extract clean email address from "Name <email@example.com>"
        String senderEmail = extractEmail(from);

        String gmailCategory = "PRIMARY";
        List<String> labels  = msg.getLabelIds();

        if (labels != null) {
            if (labels.contains("CATEGORY_PROMOTIONS"))   gmailCategory = "PROMOTIONS";
            else if (labels.contains("CATEGORY_SOCIAL"))  gmailCategory = "SOCIAL";
            else if (labels.contains("CATEGORY_UPDATES")) gmailCategory = "UPDATES";
        }

        logger.info("Fetching [{}] → {}", gmailCategory, subject);

        String body = extract(msg.getPayload());
        if (body.isBlank()) body = subject;

        // ── CALL AI SERVICE ───────────────────────────────────────────────────
        Map<String, Object> aiResult;
        try {
            Map<String, String> request = Map.of(
                "text",     body.substring(0, Math.min(3000, body.length())),
                "subject",  subject,
                "email_id", messageId
            );
            aiResult = rt.postForObject("http://127.0.0.1:5001/analyze", request, Map.class);
        } catch (Exception e) {
            logger.error("AI analysis failed for email {}: {}", messageId, e.getMessage());
            aiResult = Map.of(
                "category", "Other",
                "priority", "LOW",
                "summary",  "Analysis unavailable",
                "reply",    "No reply generated"
            );
        }
        // ─────────────────────────────────────────────────────────────────────

        LocalDateTime mailDate;
        try {
            mailDate = ZonedDateTime
                    .parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toLocalDateTime();
        } catch (Exception ex) {
            mailDate = Instant.ofEpochMilli(msg.getInternalDate())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        // ── SAVE OR UPDATE ────────────────────────────────────────────────────
        if (alreadyExists) {
            Email existing = repo.findByMessageIdAndUserId(messageId, uid);
            existing.setSummary(String.valueOf(aiResult.get("summary")));
            existing.setReply(String.valueOf(aiResult.get("reply")));
            existing.setCategory(String.valueOf(aiResult.get("category")));
            existing.setPriority(String.valueOf(aiResult.get("priority")));
            // ✅ Backfill senderEmail on update too
            if (existing.getSenderEmail() == null || existing.getSenderEmail().isBlank()) {
                existing.setSenderEmail(senderEmail);
            }
            repo.save(existing);
            logger.info("Updated email: {} | priority: {} | category: {}",
                subject, existing.getPriority(), existing.getCategory());
        } else {
            Email email = new Email();
            email.setMessageId(messageId);
            email.setUserId(uid);
            email.setSubject(subject);
            email.setSender(from);
            email.setSenderEmail(senderEmail);          // ✅ NEW
            email.setContent(body);
            email.setCategory(String.valueOf(aiResult.get("category")));
            email.setPriority(String.valueOf(aiResult.get("priority")));
            email.setSummary(String.valueOf(aiResult.get("summary")));
            email.setReply(String.valueOf(aiResult.get("reply")));
            email.setDate(mailDate);
            email.setRead(false);
            repo.save(email);
            logger.info("Saved email: {} | senderEmail: {} | priority: {} | category: {}",
                subject, senderEmail, email.getPriority(), email.getCategory());
        }
        // ─────────────────────────────────────────────────────────────────────
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    // ✅ Extracts clean email address from "Display Name <email@domain.com>"
    private String extractEmail(String from) {
        if (from == null || from.isBlank()) return "";

        // Try angle-bracket format first: Name <email@example.com>
        Matcher m = Pattern
            .compile("<([a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,})>")
            .matcher(from);
        if (m.find()) return m.group(1);

        // Fallback: bare email address
        m = Pattern
            .compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")
            .matcher(from);
        if (m.find()) return m.group();

        return "";
    }

    private String cleanHtmlEmail(String html) {
        if (html == null || html.isBlank()) return "";

        try {
            Document doc = Jsoup.parse(html);
            doc.select("style,script,head,meta,link").remove();

            String text = doc.text();
            text = text.replaceAll("(?i)unsubscribe.*", "");
            text = text.replaceAll("(?i)not interested\\?.*", "");
            text = text.replaceAll("(?i)learn more.*", "");
            text = text.replaceAll("(?i)©.*", "");

            return text.trim();
        } catch (Exception e) {
            logger.error("Error cleaning HTML: {}", e.getMessage());
            return "";
        }
    }

    private String extract(MessagePart part) {
        if (part == null) return "";

        if ("text/plain".equalsIgnoreCase(part.getMimeType()) && part.getBody() != null) {
            return decode(part.getBody().getData());
        }

        if ("text/html".equalsIgnoreCase(part.getMimeType()) && part.getBody() != null) {
            return cleanHtmlEmail(decode(part.getBody().getData()));
        }

        if (part.getParts() != null) {
            for (MessagePart p : part.getParts()) {
                String s = extract(p);
                if (!s.isBlank()) return s;
            }
        }

        return "";
    }

    private String decode(String data) {
        if (data == null) return "";
        try {
            return new String(Base64.getUrlDecoder().decode(data));
        } catch (Exception e) {
            logger.error("Error decoding base64: {}", e.getMessage());
            return "";
        }
    }
}