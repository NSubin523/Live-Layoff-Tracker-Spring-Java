package com.example.layoff_tracker.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class FirebaseAuthValidator {
    private final FirebaseAuth firebaseAuth;

    public String extractUserId(String bearerHeader) {
        if (bearerHeader == null || !bearerHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header"
            );
        }

        String idToken = bearerHeader.substring(7).trim();

        try {
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
            String uid = decodedToken.getUid();
            if (uid == null || uid.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token: missing UID");
            }
            return uid;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token verification failed: " + e.getMessage()
            );
        }
    }
}
