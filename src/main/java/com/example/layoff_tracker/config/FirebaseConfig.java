package com.example.layoff_tracker.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials-path}")
    private Resource credentialsResource;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            // Read bytes completely into memory first so the stream cannot be closed prematurely
            byte[] credentialsBytes;
            try (InputStream is = credentialsResource.getInputStream()) {
                credentialsBytes = is.readAllBytes();
            }

            try (InputStream serviceAccountStream = new ByteArrayInputStream(credentialsBytes)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream);
                HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setHttpTransport(httpTransport)
                        .build();

                return FirebaseApp.initializeApp(options);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
        return FirebaseApp.getInstance();
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore(firebaseApp);
    }
}
