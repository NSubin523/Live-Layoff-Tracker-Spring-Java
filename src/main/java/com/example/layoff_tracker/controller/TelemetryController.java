package com.example.layoff_tracker.controller;

import com.example.layoff_tracker.dto.TelemetryBatchRequestDto;
import com.example.layoff_tracker.security.FirebaseAuthValidator;
import com.example.layoff_tracker.service.TelemetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
@Slf4j
public class TelemetryController {
    private final TelemetryService service;
    private final FirebaseAuthValidator authValidator;

    @PostMapping("/batch")
    public ResponseEntity<Void> batch(@Valid @RequestBody TelemetryBatchRequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = authorization == null ? "Non-User" : authValidator.extractUserId(authorization);
        try {
            service.insertBatch(userId, request.events());
        } catch (RuntimeException error) {
            log.error("Unable to save telemetry batch", error);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save telemetry. Please try again.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleFailure(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("detail", error.getReason() == null ? "Request failed" : error.getReason()));
    }
}
