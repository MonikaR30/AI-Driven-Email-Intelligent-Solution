package com.example.emailintelligence.util;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collections;

@Component
public class GmailUtil {

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    @Value("${google.redirect.uri}")
    private String redirectUri;

    @Value("${google.token.directory:${user.home}/gmail-token}")
    private String tokenDirectory;

    private GoogleAuthorizationCodeFlow flow;

    public GoogleAuthorizationCodeFlow getFlow() throws Exception {
        if (flow == null) {
            File tokenDir = new File(tokenDirectory);
            if (!tokenDir.exists()) {
                tokenDir.mkdirs();
            }

            flow = new GoogleAuthorizationCodeFlow.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientId,
                    clientSecret,
                    Collections.singleton(GmailScopes.GMAIL_READONLY)
            )
            .setDataStoreFactory(new FileDataStoreFactory(tokenDir))
            .setAccessType("offline")
            .build();
        }
        return flow;
    }

    public Gmail getGmailForUser(String uid) throws Exception {
        GoogleAuthorizationCodeFlow flow = getFlow();
        var credential = flow.loadCredential(uid);

        if (credential == null) {
            throw new RuntimeException("No Gmail credential found for user: " + uid);
        }

        return new Gmail.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
        ).setApplicationName("AI Email Intelligence").build();
    }
}