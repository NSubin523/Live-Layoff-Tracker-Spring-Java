package com.example.layoff_tracker.repository;

import com.example.layoff_tracker.mapper.CompanyMapper;
import com.example.layoff_tracker.model.Company;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserFavoriteRepositoryImpl implements UserFavoriteRepository{

    private final Firestore firestore;

    private DocumentReference getFavoriteDocRef(String userId, String companyId) {
        return firestore.collection("users")
                .document(userId)
                .collection("favorites")
                .document(companyId);
    }


    /** Idempotent membership write. Repeated saves preserve the original savedAt. */
    @Override
    public void setFavorite(String userId, String companyId, boolean saved) {
        DocumentReference ref = getFavoriteDocRef(userId, companyId);
        try {
            firestore.runTransaction(transaction -> {
                DocumentSnapshot current = transaction.get(ref).get();
                if (saved && !current.exists()) {
                    transaction.set(ref, Map.of("companyId", companyId, "savedAt", FieldValue.serverTimestamp()));
                } else if (!saved && current.exists()) {
                    transaction.delete(ref);
                }
                return null;
            }).get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while setting favorite", error);
        } catch (ExecutionException error) {
            throw new RuntimeException("Unable to set favorite", error);
        }
    }

    /** Membership is returned even when the referenced layoff document no longer exists. */
    @Override
    public List<String> getFavoriteIds(String userId) {
        try {
            return firestore.collection("users").document(userId).collection("favorites")
                    .get().get().getDocuments().stream().map(DocumentSnapshot::getId).toList();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while reading favorite IDs", error);
        } catch (ExecutionException error) {
            throw new RuntimeException("Unable to read favorite IDs", error);
        }
    }

    @Override
    public boolean isFavorite(String userId, String companyId) {
        try {
            ApiFuture<DocumentSnapshot> future = getFavoriteDocRef(
                    userId, companyId
            ).get();
            return future.get().exists();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while checking favorite status", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to check favorite status in Firestore", e);
        }
    }

    @Override
    public void addFavorite(String userId, String companyId) throws ExecutionException, InterruptedException {
        try {
            HashMap<String, Object> data = new HashMap<>();
            data.put("companyId", companyId);
            data.put("savedAt", FieldValue.serverTimestamp());

            ApiFuture<WriteResult> future = getFavoriteDocRef(userId, companyId).set(data);
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while adding to favorites", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to delete favorite from Firestore", e);
        }
    }

    @Override
    public void removeFavorite(String userId, String companyId) {
        try {
            ApiFuture<WriteResult> future = getFavoriteDocRef(userId, companyId).delete();
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while removing favorite", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to delete favorite from Firestore", e);
        }
    }

    @Override
    public List<Company> getUserFavorites(String userId) {
        try {
            // 1. Fetch user's saved bookmark IDs sorted by timestamp
            ApiFuture<QuerySnapshot> favSnapshot = firestore.collection("users")
                    .document(userId)
                    .collection("favorites")
                    .orderBy("savedAt", Query.Direction.DESCENDING)
                    .get();

            List<QueryDocumentSnapshot> favDocs = favSnapshot.get().getDocuments();
            if (favDocs.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> companyIds = favDocs.stream()
                    .map(DocumentSnapshot::getId)
                    .toList();

            // 2. Hydrate from "layoffs" collection using whereIn batches (Firestore limit: 30)
            List<Company> companies = new ArrayList<>();
            int chunkSize = 30;

            for (int i = 0; i < companyIds.size(); i += chunkSize) {
                List<String> chunk = companyIds.subList(i, Math.min(i + chunkSize, companyIds.size()));
                ApiFuture<QuerySnapshot> companyQuery = firestore.collection("layoffs")
                        .whereIn(FieldPath.documentId(), chunk)
                        .get();

                for (DocumentSnapshot doc : companyQuery.get().getDocuments()) {
                    Company company = CompanyMapper.fromDocument(doc);
                    if (company != null) {
                        companies.add(company);
                    }
                }
            }

            // 3. Preserve user's saved-at chronological order
            Map<String, Integer> orderMap = new HashMap<>();
            for (int i = 0; i < companyIds.size(); i++) {
                orderMap.put(companyIds.get(i), i);
            }
            companies.sort(Comparator.comparingInt(c -> orderMap.getOrDefault(c.getId(), Integer.MAX_VALUE)));

            return companies;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while fetching user favorites", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to fetch user favorites from Firestore", e);
        }
    }
}
