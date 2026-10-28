package com.example.layoff_tracker.service;
import com.example.layoff_tracker.repository.UserFavoriteRepository;
import com.example.layoff_tracker.analytics.CompanyIdentityResolver;
import com.example.layoff_tracker.model.Company;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserFavoriteServiceTest {
    UserFavoriteRepository repository = mock(UserFavoriteRepository.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") ValueOperations<String,String> values = mock(ValueOperations.class);
    UserFavoriteService service;
    CompanyIdentityResolver identityResolver = mock(CompanyIdentityResolver.class);
    @BeforeEach void setup() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("lock:fav:u:c"), eq("LOCKED"), any(Duration.class))).thenReturn(true);
        service = new UserFavoriteService(repository, redis, identityResolver);
    }
    @Test void repeatedSaveSetsStateWithoutReadingOrToggling() {
        assertTrue(service.setFavorite("u", "c", true).isSaved());
        assertTrue(service.setFavorite("u", "c", true).isSaved());
        verify(repository, times(2)).setFavorite("u", "c", true);
        verify(repository, never()).isFavorite(anyString(), anyString());
        verifyNoMoreInteractions(repository);
    }
    @Test void unsaveForwardsExplicitFalse() {
        assertFalse(service.setFavorite("u", "c", false).isSaved());
        verify(repository).setFavorite("u", "c", false);
    }
    @Test void redisRateLimitPreventsPersistence() {
        when(values.setIfAbsent(eq("lock:fav:u:c"), eq("LOCKED"), any(Duration.class))).thenReturn(false);
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> service.setFavorite("u", "c", true)).getStatusCode().value());
        verifyNoInteractions(repository);
    }
    @Test void storageFailureEvictsLockAndReturnsServerError() {
        doThrow(new RuntimeException("unavailable")).when(repository).setFavorite("u", "c", true);
        assertEquals(500, assertThrows(ResponseStatusException.class, () -> service.setFavorite("u", "c", true)).getStatusCode().value());
        verify(redis).delete("lock:fav:u:c");
    }
    @Test void favoritesRetainLegacyIdsAndGainOptionalAnalyticsIdentity() {
        var resolved = Company.builder().id("legacy-a").companyName("A").build();
        var unresolved = Company.builder().id("legacy-b").companyName("B").build();
        when(repository.getUserFavorites("u")).thenReturn(java.util.List.of(resolved, unresolved));
        when(identityResolver.resolveFavorites(java.util.List.of("legacy-a", "legacy-b")))
            .thenReturn(java.util.Map.of("legacy-a", "stable-id"));
        var results = service.getUserFavorites("u");
        assertEquals("legacy-a", results.getFirst().getId());
        assertEquals("stable-id", results.getFirst().getAnalyticsCompanyId());
        assertNull(results.get(1).getAnalyticsCompanyId());
    }
}
