package com.example.layoff_tracker.repository;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserFavoriteRepositoryTest {
    @Test @SuppressWarnings("unchecked") void repeatedWritesAreIdempotentAndPreserveSavedTimestamp() throws Exception {
        Firestore db = mock(Firestore.class);
        CollectionReference users = mock(CollectionReference.class), favorites = mock(CollectionReference.class);
        DocumentReference user = mock(DocumentReference.class), company = mock(DocumentReference.class);
        Transaction transaction = mock(Transaction.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        AtomicBoolean exists = new AtomicBoolean(false);
        when(db.collection("users")).thenReturn(users);
        when(users.document("u")).thenReturn(user);
        when(user.collection("favorites")).thenReturn(favorites);
        when(favorites.document("c")).thenReturn(company);
        when(snapshot.exists()).thenAnswer(call -> exists.get());
        when(transaction.get(company)).thenReturn(ApiFutures.immediateFuture(snapshot));
        when(transaction.set(eq(company), any(Map.class))).thenAnswer(call -> { exists.set(true); return transaction; });
        when(transaction.delete(company)).thenAnswer(call -> { exists.set(false); return transaction; });
        when(db.runTransaction(any(Transaction.Function.class))).thenAnswer(call -> {
            Transaction.Function<Object> callback = call.getArgument(0);
            return ApiFutures.immediateFuture(callback.updateCallback(transaction));
        });
        UserFavoriteRepositoryImpl repository = new UserFavoriteRepositoryImpl(db);
        repository.setFavorite("u", "c", true);
        repository.setFavorite("u", "c", true);
        assertTrue(exists.get());
        verify(transaction, times(1)).set(eq(company), argThat((Map<String,Object> data) -> data.containsKey("savedAt") && data.get("companyId").equals("c")));
        repository.setFavorite("u", "c", false);
        repository.setFavorite("u", "c", false);
        assertFalse(exists.get());
        verify(transaction, times(1)).delete(company);
    }
}
