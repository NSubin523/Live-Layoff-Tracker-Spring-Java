package com.example.layoff_tracker.service;

import com.example.layoff_tracker.dto.FeedResponseDto;
import com.example.layoff_tracker.repository.FeedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedServiceTest {
    private final FeedRepository repository = mock(FeedRepository.class);
    private final FeedService service = new FeedService(repository);

    @Test void usesExistingFeedLimitAndPreservesRepositoryOrder() {
        List<FeedResponseDto> feed = List.of(new FeedResponseDto("new", "Meta", 42L,
                "Rumored", "Software", "Global", "2026-10-12T00:00:00Z", "logo", "stable"));
        when(repository.findLatest(25)).thenReturn(feed);
        assertEquals(feed, service.getFeed());
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
}
