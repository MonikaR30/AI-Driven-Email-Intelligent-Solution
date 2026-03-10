package com.example.emailintelligence.controller;

import com.example.emailintelligence.service.GmailSyncService;
import com.example.emailintelligence.util.GmailUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gmail")
@CrossOrigin("*")
public class GmailController {

    @Autowired
    GmailSyncService syncService;
@Autowired
private GmailUtil gmailUtil;
   @GetMapping("/connect")
public void connect(@RequestParam Long uid, HttpServletResponse response) throws Exception {

    GoogleAuthorizationCodeFlow flow = gmailUtil.getFlow();

    String url = flow.newAuthorizationUrl()
            .setRedirectUri("http://localhost:8080/api/gmail/callback")
            .setState(uid.toString())     // store uid safely
            .setAccessType("offline")
            .build();

    response.sendRedirect(url);
}


   @GetMapping("/callback")
public void callback(@RequestParam String code,
                     @RequestParam String state,
                     HttpServletResponse response) throws Exception {

    Long uid = Long.parseLong(state);

    GoogleAuthorizationCodeFlow flow = gmailUtil.getFlow();

    // 1️⃣ Exchange code ONCE
    GoogleTokenResponse tokenResponse = flow.newTokenRequest(code)
            .setRedirectUri("http://localhost:8080/api/gmail/callback")
            .execute();

    // 2️⃣ STORE refresh token properly
    flow.createAndStoreCredential(tokenResponse, uid.toString());

    // 3️⃣ Trigger sync (NO CODE)
    syncService.sync(uid);

    response.sendRedirect(
        "http://localhost:5500/frontend/dashboard.html?sync=done"
    );
}


}

