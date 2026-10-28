package com.example.layoff_tracker.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static com.example.layoff_tracker.analytics.AnalyticsDto.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyAnalyticsServiceTest {
    CompanyAnalyticsRepository repository = mock(CompanyAnalyticsRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);
    CompanyAnalyticsService service = new CompanyAnalyticsService(repository, clock);
    String id = AnalyticsQueryTest.ID;
    UUID uuid = UUID.fromString(id);

    @BeforeEach void defaults() {
        when(repository.findCompany(uuid)).thenReturn(Optional.of(new Company(id, "Synthetic", null, "UNKNOWN")));
        when(repository.workforce(eq(uuid), any())).thenReturn(Optional.empty());
        when(repository.monthlyBuckets(any())).thenReturn(List.of());
        when(repository.events(any(), isNull())).thenAnswer(invocation -> {
            AnalyticsQuery q = invocation.getArgument(0);
            return new EventPage(id, q.period(), List.of(), null);
        });
        when(repository.coverage(eq(uuid), anyString())).thenReturn(new CompanyAnalyticsRepository.CoverageState(
                new Coverage(null, null, null, "UNKNOWN"), null, null, null));
    }
    @Test void noHistoryIsNotAdvertisedAsProofOfZeroLayoffs() {
        var result = service.dashboard(id, "2026-01-15", "2026-03-04");
        assertEquals(Availability.NO_DATA, result.layoffs().availability());
        assertEquals(Freshness.UNKNOWN, result.layoffs().freshness());
        assertTrue(result.layoffs().warnings().contains("COVERAGE_IS_INCOMPLETE"));
        assertNull(result.workforce().data());
        var months = result.layoffs().data().monthlyBuckets();
        assertEquals(3, months.size());
        assertEquals(LocalDate.of(2026, 1, 15), months.getFirst().from());
        assertEquals(LocalDate.of(2026, 3, 4), months.getLast().to());
    }
    @Test void summaryUsesFullPeriodBucketsRatherThanFirstEventPage() {
        when(repository.monthlyBuckets(any())).thenReturn(List.of(
                new Bucket(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 31), 500, 60, 2, 3, 4, 5)));
        var result = service.dashboard(id, "2026-01-15", "2026-03-04");
        assertEquals(500, result.layoffs().data().reportedAffectedCount());
        assertEquals(60, result.layoffs().data().confirmedEventCount());
        assertTrue(result.layoffs().data().events().isEmpty());
        assertEquals(Availability.PARTIAL, result.layoffs().availability());
        assertTrue(result.layoffs().warnings().contains("UNRESOLVED_OVERLAPS_EXCLUDED_FROM_SUM"));
    }
    @Test void oneFailedSectionDoesNotDestroyTheDashboard() {
        when(repository.workforce(eq(uuid), any())).thenThrow(new IllegalStateException("private database details"));
        var result = service.dashboard(id, null, null);
        assertEquals(Availability.TEMPORARILY_UNAVAILABLE, result.workforce().availability());
        assertEquals(Availability.NO_DATA, result.layoffs().availability());
    }
    @Test void allSectionFailuresProduce503() {
        when(repository.workforce(eq(uuid), any())).thenThrow(new IllegalStateException());
        when(repository.monthlyBuckets(any())).thenThrow(new IllegalStateException());
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.dashboard(id, null, null)).getStatusCode().value());
    }
    @Test void failedLayoffSectionStillReturnsKnownWorkforce() {
        when(repository.monthlyBuckets(any())).thenThrow(new IllegalStateException());
        when(repository.workforce(eq(uuid), any())).thenReturn(Optional.of(new Workforce(id, 1000L, null,
                LocalDate.of(2025, 12, 31), "COMPANY_REPORTED", new Scope("GLOBAL", "CONSOLIDATED", "EMPLOYEES"), List.of())));
        var result = service.dashboard(id, null, null);
        assertEquals(Availability.TEMPORARILY_UNAVAILABLE, result.layoffs().availability());
        assertEquals(1000L, result.workforce().data().employeeCount());
    }
    @Test void unknownCompanyIs404AndIdentityStorageFailureIs503() {
        when(repository.findCompany(uuid)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.dashboard(id, null, null)).getStatusCode().value());
        verify(repository, never()).monthlyBuckets(any());
        when(repository.findCompany(uuid)).thenThrow(new IllegalStateException());
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.dashboard(id, null, null)).getStatusCode().value());
    }
    @Test void staleFactsRemainDatedAndFailedRefreshIsVisible() {
        var date = LocalDate.of(2025, 12, 31);
        when(repository.workforce(eq(uuid), any())).thenReturn(Optional.of(new Workforce(id, 1000L, null,
                date, "COMPANY_REPORTED", new Scope("GLOBAL", "PARENT", "EMPLOYEES"), List.of())));
        when(repository.coverage(uuid, "WORKFORCE")).thenReturn(new CompanyAnalyticsRepository.CoverageState(
                new Coverage(date, date, "GLOBAL", "PARTIAL"), Instant.parse("2026-10-20T00:00:00Z"),
                Instant.parse("2026-10-21T00:00:00Z"), "FAILED"));
        var result = service.dashboard(id, null, null);
        assertEquals(Freshness.STALE, result.workforce().freshness());
        assertEquals(date, result.workforce().data().asOf());
        assertTrue(result.workforce().warnings().contains("LAST_REFRESH_FAILED"));
    }
}
