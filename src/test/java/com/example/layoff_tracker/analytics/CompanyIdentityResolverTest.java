package com.example.layoff_tracker.analytics;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyIdentityResolverTest {
    CompanyAnalyticsRepository repository = mock(CompanyAnalyticsRepository.class);
    CompanyIdentityResolver resolver = new CompanyIdentityResolver(repository);
    @Test void missingMigrationDoesNotBreakExistingFavorites() {
        when(repository.resolveLegacyIds(List.of("legacy"))).thenThrow(new IllegalStateException("schema missing"));
        assertEquals(Map.of(), resolver.resolveFavorites(List.of("legacy")));
    }
    @Test void emptyFavoritesDoNotRequireAnalyticsStorage() {
        assertTrue(resolver.resolveFavorites(List.of()).isEmpty());
        verifyNoInteractions(repository);
    }
}
