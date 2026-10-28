package com.example.layoff_tracker.service;

import com.example.layoff_tracker.repository.FeedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import com.example.layoff_tracker.model.FeedCompany;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedServiceTest {
    private final FeedRepository repository = mock(FeedRepository.class);
    private final FeedService service = new FeedService(repository);

    @Test void usesExistingFeedLimitAndPreservesRepositoryOrder() {
        List<FeedCompany> feed = List.of(new FeedCompany("new", "Meta", 42L,
                "Rumored", "Software", "Global", Instant.parse("2026-10-12T00:00:00Z"), "logo", "stable",
                null, null, null, null, null));
        when(repository.findLatest(25)).thenReturn(feed);
        var result = service.getFeed().getFirst();
        assertEquals("new", result.id());
        assertEquals("Rumored", result.status());
        assertEquals("2026-10-12T00:00:00Z", result.reportedAt());
        verify(repository).findLatest(25);
    }

    @Test void emptyCollectionReturnsEmptyList() {
        when(repository.findLatest(25)).thenReturn(List.of());
        assertTrue(service.getFeed().isEmpty());
    }

    @Test void hidesInfrastructureFailureFromPublicResponse() {
        RuntimeException failure = new IllegalStateException("private infrastructure detail");
        when(repository.findLatest(25)).thenThrow(failure);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, service::getFeed);
        assertEquals(500, error.getStatusCode().value());
        assertSame(failure, error.getCause());
        assertFalse(error.getReason().contains("private"));
    }
    @Test void detailUsesLegacyFeedIdAndReturnsOnlyTheRequestedRecord() {
        var detail = new FeedCompany("legacy", "Example", null, "Rumored", null, null,
                Instant.parse("2026-10-28T00:00:00Z"), null, null, null, "Long details", null, null, null);
        when(repository.findById("legacy")).thenReturn(Optional.of(detail));
        var result = service.getDetail("legacy");
        assertEquals("legacy", result.id());
        assertEquals("Long details", result.layoffDetails());
        verify(repository).findById("legacy");
        verify(repository, never()).findLatest(anyInt());
    }
    @Test void detailReturns404ForMissingRecordsAnd500ForStorageFailures() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.getDetail("missing")).getStatusCode().value());
        var failure = new IllegalStateException("private infrastructure detail");
        when(repository.findById("broken")).thenThrow(failure);
        var error = assertThrows(ResponseStatusException.class, () -> service.getDetail("broken"));
        assertEquals(500, error.getStatusCode().value());
        assertSame(failure, error.getCause());
        assertEquals("Unable to fetch feed details. Please try again.", error.getReason());
    }
    @Test void invalidDocumentIdsNeverReachFirestore() {
        for (String id : List.of(" ", ".", "..", "nested/document", "__reserved__", "x".repeat(1501), "é".repeat(751))) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.getDetail(id)).getStatusCode().value());
        }
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.getDetail(null)).getStatusCode().value());
        verifyNoInteractions(repository);
    }
    @Test void feedCarriesTheSameFullPublicPayloadAsTheDetailFallback() {
        var company = new FeedCompany("legacy", "Example", 50L, "Confirmed", "Software", "Global",
                Instant.parse("2026-10-28T00:00:00Z"), "https://example.test/logo", "stable", "Company description",
                "Long layoff details.\nMore details.", List.of(new FeedCompany.NewsSource("Example News", "https://news.example.test")),
                List.of(new FeedCompany.Image("https://example.test/image", "Office", null, null)),
                List.of(new FeedCompany.ArticleLink("https://news.example.test/story", "Story", "Example News", Instant.parse("2026-10-27T00:00:00Z"))));
        when(repository.findLatest(25)).thenReturn(List.of(company));
        when(repository.findById("legacy")).thenReturn(Optional.of(company));
        var response = service.getFeed().getFirst();
        assertEquals(service.getDetail("legacy"), response);
        assertEquals(company.layoffDetails(), response.layoffDetails());
        assertEquals("Example News", response.newsSources().getFirst().name());
        assertEquals("Office", response.images().getFirst().altText());
        assertEquals("2026-10-27T00:00:00Z", response.articleLinks().getFirst().publishedAt());
    }
}
