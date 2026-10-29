package com.example.layoff_tracker.analytics;

import com.example.layoff_tracker.security.FirebaseAuthValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}")
@RequiredArgsConstructor
@Slf4j
public class CompanyAnalyticsController {
    private final CompanyAnalyticsService service;
    private final FirebaseAuthValidator auth;

    @GetMapping("/analytics")
    public AnalyticsDto.Dashboard dashboard(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String companyId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        auth.extractUserId(authorization);
        return service.dashboard(companyId, from, to);
    }

    @GetMapping("/layoff-events")
    public AnalyticsDto.EventPage events(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String companyId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String cursor) {
        auth.extractUserId(authorization);
        return service.events(companyId, from, to, cursor);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<AnalyticsDto.Error> failure(ResponseStatusException e) {
        String code = switch (e.getStatusCode().value()) {
            case 400 -> "INVALID_QUERY";
            case 401 -> "UNAUTHORIZED";
            case 404 -> "COMPANY_NOT_FOUND";
            default -> "ANALYTICS_UNAVAILABLE";
        };
        String detail = e.getStatusCode().value() == 401 ? "A valid Firebase bearer token is required" : e.getReason();
        String requestId = UUID.randomUUID().toString();
        log.warn("Analytics request {} failed with code {}", requestId, code);
        return ResponseEntity.status(e.getStatusCode()).header("Cache-Control", "no-store")
                .header("X-Request-ID", requestId).body(new AnalyticsDto.Error(code, detail, requestId));
    }
}
