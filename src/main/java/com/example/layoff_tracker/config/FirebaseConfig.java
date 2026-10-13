package com.example.layoff_tracker.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
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

    @Value("${firebase.use-adc:false}")
    private boolean useAdc;

    @Value("${firebase.project-id:}")
    private String projectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            GoogleCredentials credentials;
            if (useAdc) {
                credentials = GoogleCredentials.getApplicationDefault();
            } else {
                byte[] credentialsBytes;
                try (InputStream is = credentialsResource.getInputStream()) {
                    credentialsBytes = is.readAllBytes();
                }
                try (InputStream serviceAccountStream = new ByteArrayInputStream(credentialsBytes)) {
                    credentials = GoogleCredentials.fromStream(serviceAccountStream);
                }
            }

            try {
                HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

                FirebaseOptions.Builder options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setHttpTransport(httpTransport);
                if (!projectId.isBlank()) {
                    options.setProjectId(projectId);
                }

                return FirebaseApp.initializeApp(options.build());
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
