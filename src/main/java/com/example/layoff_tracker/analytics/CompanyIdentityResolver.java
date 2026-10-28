package com.example.layoff_tracker.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyIdentityResolver {
    private final CompanyAnalyticsRepository repository;

    /** Analytics rollout or storage failures must not take down existing favorites. */
    public Map<String, String> resolveFavorites(List<String> legacyIds) {
        if (legacyIds.isEmpty()) return Map.of();
        try { return repository.resolveLegacyIds(legacyIds); }
        catch (RuntimeException e) {
            log.warn("Analytics identity mapping unavailable; serving favorites without analytics IDs", e);
            return Map.of();
        }
    }
}
