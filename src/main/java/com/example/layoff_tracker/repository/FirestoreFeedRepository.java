package com.example.layoff_tracker.repository;

import com.example.layoff_tracker.dto.FeedResponseDto;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
@RequiredArgsConstructor
public class FirestoreFeedRepository implements FeedRepository {
    private final Firestore firestore;

    @Override
    public List<FeedResponseDto> findLatest(int limit) {
        try {
            return firestore.collection("layoffs")
                    .orderBy("reported_at", Query.Direction.DESCENDING)
                    .limit(limit).get().get().getDocuments().stream()
                    .map(FirestoreFeedRepository::toResponse).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching feed", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to fetch feed from Firestore", e);
        }
    }

    private static FeedResponseDto toResponse(DocumentSnapshot doc) {
        Timestamp timestamp = doc.getTimestamp("reported_at");
        if (timestamp == null) {
            throw new IllegalStateException("Feed record is missing reported_at");
        }
        String reportedAt = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toString();
        return new FeedResponseDto(doc.getId(), doc.getString("company_name"),
                doc.getLong("impact_count"), doc.getString("status"),
                doc.getString("industry"), doc.getString("location"), reportedAt,
                doc.getString("logo_url"), doc.getString("trend_direction"));
    }
}
