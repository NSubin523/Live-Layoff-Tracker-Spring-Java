package com.example.layoff_tracker.analytics;

import com.example.layoff_tracker.security.FirebaseAuthValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompanyAnalyticsController.class)
@Import(CompanyAnalyticsService.class)
class CompanyAnalyticsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CompanyAnalyticsRepository repository;
    @MockitoBean FirebaseAuthValidator auth;
    String path = "/api/v1/companies/" + AnalyticsQueryTest.ID;

    @BeforeEach void defaults() {
        when(auth.extractUserId(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing"));
        when(auth.extractUserId("Bearer valid")).thenReturn("trusted");
        when(repository.findCompany(any())).thenReturn(Optional.of(new AnalyticsDto.Company(AnalyticsQueryTest.ID, "Synthetic", null, "PRIVATE")));
        when(repository.workforce(any(), any())).thenReturn(Optional.empty());
        when(repository.monthlyBuckets(any())).thenReturn(List.of());
        when(repository.events(any(), any())).thenAnswer(invocation -> {
            AnalyticsQuery q = invocation.getArgument(0);
            return new AnalyticsDto.EventPage(q.companyId().toString(), q.period(), List.of(), null);
        });
        when(repository.coverage(any(), anyString())).thenReturn(new CompanyAnalyticsRepository.CoverageState(
                new AnalyticsDto.Coverage(null, null, null, "UNKNOWN"), null, null, null));
    }
    @Test void analyticsRequiresAuthBeforeAnyStorageAccess() throws Exception {
        mvc.perform(get(path + "/analytics")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        mvc.perform(get(path + "/layoff-events")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }
    @Test void dashboardReturnsSnakeCaseDatedPartialData() throws Exception {
        mvc.perform(get(path + "/analytics").header("Authorization", "Bearer valid")
                .param("from", "2026-01-15").param("to", "2026-03-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema_version").value(1))
                .andExpect(jsonPath("$.generated_at").isNotEmpty())
                .andExpect(jsonPath("$.period.event_date_basis").value("ANNOUNCEMENT_DATE"))
                .andExpect(jsonPath("$.workforce.availability").value("NO_DATA"))
                .andExpect(jsonPath("$.layoffs.data.monthly_buckets.length()").value(3))
                .andExpect(jsonPath("$.layoffs.data.monthly_buckets[0].from").value("2026-01-15"))
                .andExpect(jsonPath("$.layoffs.data.monthly_buckets[2].to").value("2026-03-04"))
                .andExpect(jsonPath("$.company.listing_status").value("PRIVATE"))
                .andExpect(jsonPath("$.schemaVersion").doesNotExist());
    }
    @Test void invalidQueryAndUnknownCompanyHaveDistinctErrors() throws Exception {
        mvc.perform(get(path + "/analytics").header("Authorization", "Bearer valid").param("from", "2026-01-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        verifyNoInteractions(repository);
        when(repository.findCompany(any())).thenReturn(Optional.empty());
        mvc.perform(get(path + "/analytics").header("Authorization", "Bearer valid"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }
    @Test void failuresHaveSafeDetailsAndRequestIds() throws Exception {
        when(repository.findCompany(any())).thenThrow(new IllegalStateException("secret database host/password"));
        mvc.perform(get(path + "/analytics").header("Authorization", "Bearer valid"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Analytics temporarily unavailable"))
                .andExpect(jsonPath("$.request_id").isNotEmpty())
                .andExpect(header().exists("X-Request-ID"));
    }
    @Test void eventPageRejectsMalformedCursorBeforeStorageAccess() throws Exception {
        mvc.perform(get(path + "/layoff-events").header("Authorization", "Bearer valid").param("cursor", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void rejectedTokenDoesNotLeakVerifierDetails() throws Exception {
        when(auth.extractUserId("Bearer expired")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal verifier detail"));
        mvc.perform(get(path + "/analytics").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("A valid Firebase bearer token is required"));
        verifyNoInteractions(repository);
    }
}
