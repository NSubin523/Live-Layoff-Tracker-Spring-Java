package com.example.layoff_tracker.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirestoreFeedDetailRepositoryTest {
    Firestore firestore = mock(Firestore.class);
    CollectionReference collection = mock(CollectionReference.class);
    DocumentReference reference = mock(DocumentReference.class);
    @SuppressWarnings("unchecked") ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);
    DocumentSnapshot doc = mock(DocumentSnapshot.class);
    FirestoreFeedRepository repository = new FirestoreFeedRepository(firestore);

    @BeforeEach void setup() throws Exception {
        when(firestore.collection("layoffs")).thenReturn(collection);
        when(collection.document("legacy-company")).thenReturn(reference);
        when(reference.get()).thenReturn(future);
        when(future.get()).thenReturn(doc);
        when(doc.exists()).thenReturn(true);
        when(doc.getId()).thenReturn("legacy-company");
        when(doc.getString("company_name")).thenReturn("Example");
        when(doc.getString("status")).thenReturn("Rumored");
        when(doc.getLong("impact_count")).thenReturn(300L);
        when(doc.getTimestamp("reported_at")).thenReturn(Timestamp.ofTimeSecondsAndNanos(1780264673L, 110526000));
    }

    @Test void readsExactDocumentWithLongTextAndStructuredEvidence() {
        String description = "A long company description. ".repeat(500);
        String details = "Reported cuts affect multiple departments.\nThe announcement is not confirmation of completed layoffs.";
        when(doc.getString("company_description")).thenReturn(description);
        when(doc.getString("layoff_details")).thenReturn(details);
        when(doc.get("news_sources")).thenReturn(List.of(Map.of("name", "Example News", "url", "https://news.example.test")));
        when(doc.get("images")).thenReturn(List.of(Map.of("url", "https://images.example.test/company.jpg",
                "alt_text", "Company office", "caption", "Headquarters", "source_url", "https://news.example.test/story")));
        when(doc.get("article_links")).thenReturn(List.of(Map.of("url", "https://news.example.test/story",
                "title", "Reported cuts", "source_name", "Example News", "published_at", Timestamp.ofTimeSecondsAndNanos(1780264673L, 110526000))));
        var result = repository.findById("legacy-company").orElseThrow();
        assertEquals("legacy-company", result.id());
        assertEquals("Example", result.companyName());
        assertEquals(300L, result.impactCount());
        assertEquals("Rumored", result.status());
        assertEquals("2026-05-31T21:57:53.110526Z", result.reportedAt().toString());
        assertEquals(description, result.companyDescription());
        assertEquals(details, result.layoffDetails());
        assertEquals("Example News", result.newsSources().getFirst().name());
        assertEquals("Company office", result.images().getFirst().altText());
        assertEquals("https://news.example.test/story", result.articleLinks().getFirst().url());
        assertEquals(result.reportedAt(), result.articleLinks().getFirst().publishedAt());
        verify(collection).document("legacy-company");
        verify(collection, never()).orderBy(anyString(), any(Query.Direction.class));
        verify(doc, never()).getData();
    }

    @Test void legacyRecordsHaveNullDescriptionsAndEmptyListsWithoutInventedContent() {
        var result = repository.findById("legacy-company").orElseThrow();
        assertNull(result.companyDescription());
        assertNull(result.layoffDetails());
        assertTrue(result.newsSources().isEmpty());
        assertTrue(result.images().isEmpty());
        assertTrue(result.articleLinks().isEmpty());
    }

    @Test void missingRecordIsDistinctFromStorageFailure() {
        when(doc.exists()).thenReturn(false);
        assertTrue(repository.findById("legacy-company").isEmpty());
        verify(doc, never()).getTimestamp(anyString());
    }

    @Test void malformedStoredCollectionsAreNotSilentlyDropped() {
        when(doc.get("images")).thenReturn("not an image array");
        assertThrows(IllegalStateException.class, () -> repository.findById("legacy-company"));
        when(doc.get("images")).thenReturn(List.of("not an object"));
        assertThrows(IllegalStateException.class, () -> repository.findById("legacy-company"));
        when(doc.get("images")).thenReturn(List.of(Map.of("caption", "Missing required URL")));
        assertThrows(IllegalStateException.class, () -> repository.findById("legacy-company"));
    }

    @Test void linksMustBeWebUrlsAndPublishedDatesMustHaveTheAgreedType() {
        when(doc.get("article_links")).thenReturn(List.of(Map.of("url", "javascript:alert(1)")));
        assertThrows(IllegalStateException.class, () -> repository.findById("legacy-company"));
        when(doc.get("article_links")).thenReturn(List.of(Map.of("url", "https://news.example.test/story", "published_at", "yesterday")));
        assertThrows(IllegalStateException.class, () -> repository.findById("legacy-company"));
        when(doc.get("article_links")).thenReturn(List.of(Map.of("url", "https://news.example.test/story")));
        assertNull(repository.findById("legacy-company").orElseThrow().articleLinks().getFirst().publishedAt());
    }

    @Test void failureRetainsItsCauseAndInterruptionRestoresFlag() throws Exception {
        var failure = new ExecutionException(new IllegalStateException("offline"));
        when(future.get()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> repository.findById("legacy-company")).getCause());
        doThrow(new InterruptedException("cancelled")).when(future).get();
        try {
            assertThrows(IllegalStateException.class, () -> repository.findById("legacy-company"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
