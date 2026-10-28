package com.example.layoff_tracker.controller;

import com.example.layoff_tracker.dto.FeedResponseDto;
import com.example.layoff_tracker.security.FirebaseAuthValidator;
import com.example.layoff_tracker.service.FeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeedController.class)
class FeedControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean FeedService service;
    @MockitoBean FirebaseAuthValidator auth;

    @Test
    void guestFeedPreservesAndroidWireContract() throws Exception {
        when(service.getFeed()).thenReturn(List.of(new FeedResponseDto("meta-1", "Meta", null,
                "Confirmed", "Software", "Bay Area", "2026-05-30T21:57:53.110526Z",
                "https://example.test/logo", "increasing")));
        mvc.perform(get("/api/v1/feed")).andExpect(status().isOk())
                .andExpect(content().json("""
                    [{"id":"meta-1","company_name":"Meta","impact_count":null,
                    "status":"Confirmed","industry":"Software","location":"Bay Area",
                    "reported_at":"2026-05-30T21:57:53.110526Z",
                    "logo_url":"https://example.test/logo","trend_direction":"increasing"}]
                    """))
                .andExpect(jsonPath("$[0].companyName").doesNotExist())
                .andExpect(jsonPath("$[0].layoffStatus").doesNotExist());
        verifyNoInteractions(auth);
    }

    @Test
    void signedInFeedVerifiesSuppliedToken() throws Exception {
        when(service.getFeed()).thenReturn(List.of());
        when(auth.extractUserId("Bearer token")).thenReturn("user-1");
        mvc.perform(get("/api/v1/feed").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(auth).extractUserId("Bearer token");
    }

    @Test
    void invalidTokenCannotFetchFeed() throws Exception {
        when(auth.extractUserId("Bearer expired")).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token verification failed"));
        mvc.perform(get("/api/v1/feed").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Token verification failed"));
        verifyNoInteractions(service);
    }

    @Test
    void repositoryFailureProducesServerError() throws Exception {
        when(service.getFeed()).thenThrow(new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unable to fetch feed. Please try again."));
        mvc.perform(get("/api/v1/feed")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unable to fetch feed. Please try again."));
    }

    @Test void guestFeedAndDetailIncludeTheSameCompleteBottomSheetPayload() throws Exception {
        var payload = new FeedResponseDto("legacy-company", "Example", 300L,
                "Confirmed", "Software", "Global", "2026-10-28T00:00:00Z", "https://example.test/logo", "stable",
                "Company description", "Layoff details.\nA second paragraph.",
                List.of(new FeedResponseDto.NewsSource("Example News", "https://news.example.test")),
                List.of(new FeedResponseDto.Image("https://example.test/office.jpg", "Office", "Headquarters", null)),
                List.of(new FeedResponseDto.ArticleLink("https://news.example.test/story", "Layoff report", "Example News", "2026-10-27T12:00:00Z")));
        when(service.getDetail("legacy-company")).thenReturn(payload);
        when(service.getFeed()).thenReturn(List.of(payload));
        String detailJson = mvc.perform(get("/api/v1/feed/legacy-company")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("legacy-company"))
                .andExpect(jsonPath("$.company_name").value("Example"))
                .andExpect(jsonPath("$.impact_count").value(300))
                .andExpect(jsonPath("$.reported_at").value("2026-10-28T00:00:00Z"))
                .andExpect(jsonPath("$.logo_url").value("https://example.test/logo"))
                .andExpect(jsonPath("$.company_description").value("Company description"))
                .andExpect(jsonPath("$.layoff_details").value("Layoff details.\nA second paragraph."))
                .andExpect(jsonPath("$.news_sources[0].name").value("Example News"))
                .andExpect(jsonPath("$.images[0].alt_text").value("Office"))
                .andExpect(jsonPath("$.article_links[0].source_name").value("Example News"))
                .andExpect(jsonPath("$.article_links[0].published_at").value("2026-10-27T12:00:00Z"))
                .andExpect(jsonPath("$.companyName").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/feed")).andExpect(status().isOk())
                .andExpect(content().json("[" + detailJson + "]"));
        verifyNoInteractions(auth);
    }
    @Test void detailValidatesSuppliedCredentialsAndRejectsInvalidTokensBeforeLoading() throws Exception {
        when(auth.extractUserId("Bearer expired")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token verification failed"));
        mvc.perform(get("/api/v1/feed/legacy-company").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        when(auth.extractUserId("Bearer token")).thenReturn("trusted");
        mvc.perform(get("/api/v1/feed/legacy-company").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(auth).extractUserId("Bearer token");
        verify(service).getDetail("legacy-company");
    }
    @Test void missingItemIs404AndStorageFailureIsSafe500() throws Exception {
        when(service.getDetail("missing")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Feed item not found"));
        mvc.perform(get("/api/v1/feed/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Feed item not found"));
        when(service.getDetail("broken")).thenThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to fetch feed details. Please try again."));
        mvc.perform(get("/api/v1/feed/broken")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unable to fetch feed details. Please try again."));
    }
    @Test void detailWithMissingOptionalFieldsHasEmptyArrays() throws Exception {
        when(service.getDetail("legacy")).thenReturn(new FeedResponseDto("legacy", "Example", null,
                null, null, null, "2026-10-28T00:00:00Z", null, null, null, null, null, null, null));
        mvc.perform(get("/api/v1/feed/legacy")).andExpect(status().isOk())
                .andExpect(jsonPath("$.news_sources").isArray()).andExpect(jsonPath("$.news_sources").isEmpty())
                .andExpect(jsonPath("$.images").isEmpty()).andExpect(jsonPath("$.article_links").isEmpty())
                .andExpect(jsonPath("$.company_description").isEmpty()).andExpect(jsonPath("$.layoff_details").isEmpty());
    }
}
