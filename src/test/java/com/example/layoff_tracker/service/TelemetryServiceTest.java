package com.example.layoff_tracker.service;
import com.example.layoff_tracker.dto.TelemetryEventDto;
import com.example.layoff_tracker.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class TelemetryServiceTest {
    TelemetryRepository repository = mock(TelemetryRepository.class);
    TelemetryService service = new TelemetryService(repository);
    @Test void emptyBatchDoesNotWrite() { service.insertBatch("Non-User",List.of()); verifyNoInteractions(repository); }
    @Test void batchUsesAuthenticatedIdentity() {
        var events=List.of(new TelemetryEventDto("spoofed","click",null));
        service.insertBatch("verified",events); verify(repository).insertBatch("verified",events);
    }
    @Test void failurePropagatesForTransactionRollback() {
        var events=List.of(new TelemetryEventDto("spoofed","click",null));
        doThrow(new IllegalStateException("failed")).when(repository).insertBatch("verified",events);
        assertThrows(IllegalStateException.class,()->service.insertBatch("verified",events));
    }
}
