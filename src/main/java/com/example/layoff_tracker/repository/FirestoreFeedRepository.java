package com.example.layoff_tracker.repository;

import com.example.layoff_tracker.model.FeedCompany;
import com.example.layoff_tracker.mapper.FeedDocumentMapper;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Repository
@RequiredArgsConstructor
public class FirestoreFeedRepository implements FeedRepository {
    private final Firestore firestore;

    @Override
    public List<FeedCompany> findLatest(int limit) {
        try {
            return firestore.collection("layoffs")
                    .orderBy("reported_at", Query.Direction.DESCENDING)
                    .limit(limit).get().get().getDocuments().stream()
                    .map(FeedDocumentMapper::fromDocument).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching feed", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to fetch feed from Firestore", e);
        }
    }

    @Override
    public Optional<FeedCompany> findById(String companyId) {
        try {
            DocumentSnapshot doc = firestore.collection("layoffs").document(companyId).get().get();
            return doc.exists() ? Optional.of(FeedDocumentMapper.fromDocument(doc)) : Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching feed details", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to fetch feed details from Firestore", e);
        }
    }

}
