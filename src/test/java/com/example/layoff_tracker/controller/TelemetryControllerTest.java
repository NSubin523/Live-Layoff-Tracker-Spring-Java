package com.example.layoff_tracker.controller;

import com.example.layoff_tracker.service.TelemetryService;
import com.example.layoff_tracker.security.FirebaseAuthValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TelemetryController.class)
class TelemetryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TelemetryService service;
    @MockitoBean FirebaseAuthValidator auth;
    private final String payload = """
        {"events":[{"user_id":"spoofed","event_name":"app_launched","event_payload":{"count":2}}]}
        """;

    @Test void guestUsesServerIdentityAndEmpty201Body() throws Exception {
        mvc.perform(post("/api/v1/telemetry/batch").contentType("application/json").content(payload))
            .andExpect(status().isCreated()).andExpect(content().string(""));
        verify(service).insertBatch(eq("Non-User"), argThat(e -> e.size() == 1 && e.get(0).eventName().equals("app_launched")));
        verifyNoInteractions(auth);
    }
    @Test void verifiedIdentityOverridesClientIdentity() throws Exception {
        when(auth.extractUserId("Bearer token")).thenReturn("verified-user");
        mvc.perform(post("/api/v1/telemetry/batch").header("Authorization", "Bearer token")
            .contentType("application/json").content(payload)).andExpect(status().isCreated());
        verify(service).insertBatch(eq("verified-user"), anyList());
    }
    @Test void invalidTokenCannotWrite() throws Exception {
        when(auth.extractUserId("Bearer expired")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid token"));
        mvc.perform(post("/api/v1/telemetry/batch").header("Authorization", "Bearer expired")
            .contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void emptyBatchAccepted() throws Exception {
        mvc.perform(post("/api/v1/telemetry/batch").contentType("application/json").content("{\"events\":[]}"))
            .andExpect(status().isCreated());
    }
    @Test void malformedAndOversizedEventsRejectedBeforeWrite() throws Exception {
        for (String body : new String[]{"{}", "{\"events\":null}", "{\"events\":[null]}",
                "{\"events\":[{}]}", "{\"events\":[{\"event_name\":\"" + "x".repeat(101) + "\"}]}"}) {
            mvc.perform(post("/api/v1/telemetry/batch").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void databaseFailureHidesInternalDetails() throws Exception {
        doThrow(new IllegalStateException("secret database password")).when(service).insertBatch(anyString(),anyList());
        mvc.perform(post("/api/v1/telemetry/batch").contentType("application/json").content(payload))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Unable to save telemetry. Please try again."));
    }
}
