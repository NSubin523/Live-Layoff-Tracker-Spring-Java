package com.example.layoff_tracker.repository;

import com.example.layoff_tracker.dto.FeedResponseDto;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirestoreFeedRepositoryTest {
    private final Firestore firestore = mock(Firestore.class);
    private final CollectionReference collection = mock(CollectionReference.class);
    private final Query ordered = mock(Query.class);
    private final Query limited = mock(Query.class);
    @SuppressWarnings("unchecked")
    private final ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
    private final QuerySnapshot snapshot = mock(QuerySnapshot.class);
    private final FirestoreFeedRepository repository = new FirestoreFeedRepository(firestore);

    @BeforeEach void setUp() throws Exception {
        when(firestore.collection("layoffs")).thenReturn(collection);
        when(collection.orderBy("reported_at", Query.Direction.DESCENDING)).thenReturn(ordered);
        when(ordered.limit(25)).thenReturn(limited);
        when(limited.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
    }

    private QueryDocumentSnapshot document(String id) {
        QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
        when(doc.getId()).thenReturn(id);
        when(doc.getString("company_name")).thenReturn("Meta");
        when(doc.getString("status")).thenReturn("Confirmed");
        when(doc.getLong("impact_count")).thenReturn(null);
        when(doc.getTimestamp("reported_at")).thenReturn(Timestamp.ofTimeSecondsAndNanos(1780264673L, 110526000));
        return doc;
    }

    @Test void queryIsLimitedAndKeepsFirestoreOrderAndNullImpact() {
        List<QueryDocumentSnapshot> documents = List.of(document("new"), document("old"));
        when(snapshot.getDocuments()).thenReturn(documents);
        List<FeedResponseDto> result = repository.findLatest(25);
        assertEquals(List.of("new", "old"), result.stream().map(FeedResponseDto::id).toList());
        assertNull(result.getFirst().impactCount());
        assertEquals("Confirmed", result.getFirst().status());
        assertEquals("2026-05-31T21:57:53.110526Z", result.getFirst().reportedAt());
        verify(ordered).limit(25);
    }

    @Test void mapsMetricsAndDisplayFieldsWithoutEnumConversion() {
        QueryDocumentSnapshot doc = document("one");
        when(doc.getLong("impact_count")).thenReturn(3000L);
        when(doc.getString("industry")).thenReturn("Software");
        when(doc.getString("location")).thenReturn("Global");
        when(doc.getString("logo_url")).thenReturn("logo");
        when(doc.getString("trend_direction")).thenReturn("increasing");
        when(snapshot.getDocuments()).thenReturn(List.of(doc));
        FeedResponseDto result = repository.findLatest(25).getFirst();
        assertEquals(3000L, result.impactCount());
        assertEquals("Software", result.industry());
        assertEquals("Global", result.location());
        assertEquals("logo", result.logoUrl());
        assertEquals("increasing", result.trendDirection());
    }

    @Test void emptyCollectionReturnsEmptyList() {
        when(snapshot.getDocuments()).thenReturn(List.of());
        assertTrue(repository.findLatest(25).isEmpty());
    }

    @Test void missingTimestampIsNotReplacedWithCurrentTime() {
        QueryDocumentSnapshot doc = document("broken");
        when(doc.getTimestamp("reported_at")).thenReturn(null);
        when(snapshot.getDocuments()).thenReturn(List.of(doc));
        assertThrows(IllegalStateException.class, () -> repository.findLatest(25));
    }

    @Test void executionFailurePreservesCause() throws Exception {
        ExecutionException failure = new ExecutionException(new IllegalStateException("offline"));
        when(future.get()).thenThrow(failure);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> repository.findLatest(25));
        assertSame(failure, error.getCause());
    }

    @Test void interruptionRestoresThreadFlag() throws Exception {
        when(future.get()).thenThrow(new InterruptedException("cancelled"));
        try {
            assertThrows(IllegalStateException.class, () -> repository.findLatest(25));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
