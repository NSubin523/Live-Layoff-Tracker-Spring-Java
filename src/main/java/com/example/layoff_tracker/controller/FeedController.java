package com.example.layoff_tracker.controller;

import com.example.layoff_tracker.dto.FeedResponseDto;
import com.example.layoff_tracker.security.FirebaseAuthValidator;
import com.example.layoff_tracker.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
public class FeedController {
    private final FeedService feedService;
    private final FirebaseAuthValidator authValidator;

    @GetMapping
    public List<FeedResponseDto> getFeed(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // Feed permits guests; supplied credentials must still be valid.
        if (authorization != null) {
            authValidator.extractUserId(authorization);
        }
        return feedService.getFeed();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleFailure(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("detail", error.getReason() == null ? "Request failed" : error.getReason()));
    }
}
