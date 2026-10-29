package com.example.layoff_tracker.service;

import com.example.layoff_tracker.dto.ToggleFavoriteResponseDto;
import com.example.layoff_tracker.analytics.CompanyIdentityResolver;
import com.example.layoff_tracker.model.Company;
import com.example.layoff_tracker.repository.UserFavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFavoriteService {

    private final UserFavoriteRepository userFavoriteRepository;
    private final StringRedisTemplate redisTemplate;
    private final CompanyIdentityResolver identityResolver;

    // Rate-limiting window to debounce button spamming
    private static final Duration DEBOUNCE_TTL = Duration.ofMillis(1200);

    public ToggleFavoriteResponseDto toggleFavorite(String userId, String companyId) {
        String lockKey = "lock:fav:" + userId + ":" + companyId;

        // Atomic Redis SETNX: returns true only if key did not previously exist
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", DEBOUNCE_TTL);

        if (Boolean.FALSE.equals(lockAcquired)) {
            log.warn("Spam debounce triggered for user [{}] on company [{}]", userId, companyId);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait a moment before toggling again."
            );
        }

        try {
            boolean isCurrentlySaved = userFavoriteRepository.isFavorite(userId, companyId);

            if (isCurrentlySaved) {
                userFavoriteRepository.removeFavorite(userId, companyId);
                log.info("Unsaved company [{}] for user [{}]", companyId, userId);
                return new ToggleFavoriteResponseDto(companyId, false, "Company removed from favorites");
            } else {
                userFavoriteRepository.addFavorite(userId, companyId);
                log.info("Saved company [{}] for user [{}]", companyId, userId);
                return new ToggleFavoriteResponseDto(companyId, true, "Company added to favorites");
            }
        } catch (Exception ex) {
            // If Firestore fails, evict lock immediately so user isn't stuck waiting out the TTL
            redisTemplate.delete(lockKey);
            log.error("Failed to toggle favorite for user [{}] on company [{}]", userId, companyId, ex);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to update favorite status. Please try again."
            );
        }
    }

    public ToggleFavoriteResponseDto setFavorite(String userId, String companyId, boolean saved) {
        String lockKey = "lock:fav:" + userId + ":" + companyId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", DEBOUNCE_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before updating again.");
        }
        try {
            userFavoriteRepository.setFavorite(userId, companyId, saved);
            return new ToggleFavoriteResponseDto(companyId, saved, saved ? "Company saved" : "Company unsaved");
        } catch (Exception error) {
            redisTemplate.delete(lockKey);
            log.error("Unable to set favorite for user [{}] company [{}]", userId, companyId, error);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to update favorite status.");
        }
    }

    public List<String> getFavoriteIds(String userId) {
        try {
            return userFavoriteRepository.getFavoriteIds(userId);
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to fetch favorite IDs.");
        }
    }

    public List<Company> getUserFavorites(String userId) {
        try {
            List<Company> favorites = userFavoriteRepository.getUserFavorites(userId);
            var analyticsIds = identityResolver.resolveFavorites(favorites.stream().map(Company::getId).toList());
            favorites.forEach(company -> company.setAnalyticsCompanyId(analyticsIds.get(company.getId())));
            return favorites;
        } catch (Exception ex) {
            log.error("Failed to fetch favorites for user [{}]", userId, ex);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to fetch user favorites."
            );
        }
    }

}
