package com.example.layoff_tracker.controller;

import com.example.layoff_tracker.dto.FeedResponseDto;
import com.example.layoff_tracker.security.FirebaseAuthValidator;
import com.example.layoff_tracker.service.FeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeedController.class)
class FeedControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean FeedService service;
    @MockitoBean FirebaseAuthValidator auth;

    @Test
    void guestFeedPreservesAndroidWireContract() throws Exception {
        when(service.getFeed()).thenReturn(List.of(new FeedResponseDto("meta-1", "Meta", null,
                "Confirmed", "Software", "Bay Area", "2026-05-30T21:57:53.110526Z",
                "https://example.test/logo", "increasing")));
        mvc.perform(get("/api/v1/feed")).andExpect(status().isOk())
                .andExpect(content().json("""
                    [{"id":"meta-1","company_name":"Meta","impact_count":null,
                    "status":"Confirmed","industry":"Software","location":"Bay Area",
                    "reported_at":"2026-05-30T21:57:53.110526Z",
                    "logo_url":"https://example.test/logo","trend_direction":"increasing"}]
                    """))
                .andExpect(jsonPath("$[0].companyName").doesNotExist())
                .andExpect(jsonPath("$[0].layoffStatus").doesNotExist());
        verifyNoInteractions(auth);
    }

    @Test
    void signedInFeedVerifiesSuppliedToken() throws Exception {
        when(service.getFeed()).thenReturn(List.of());
        when(auth.extractUserId("Bearer token")).thenReturn("user-1");
        mvc.perform(get("/api/v1/feed").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(auth).extractUserId("Bearer token");
    }

    @Test
    void invalidTokenCannotFetchFeed() throws Exception {
        when(auth.extractUserId("Bearer expired")).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token verification failed"));
        mvc.perform(get("/api/v1/feed").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Token verification failed"));
        verifyNoInteractions(service);
    }

    @Test
    void repositoryFailureProducesServerError() throws Exception {
        when(service.getFeed()).thenThrow(new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unable to fetch feed. Please try again."));
        mvc.perform(get("/api/v1/feed")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unable to fetch feed. Please try again."));
    }
}
