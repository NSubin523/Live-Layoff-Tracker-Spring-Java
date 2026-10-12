package com.example.layoff_tracker.service;

import com.example.layoff_tracker.dto.TelemetryEventDto;
import com.example.layoff_tracker.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TelemetryService {
    private final TelemetryRepository repository;

    @Transactional
    public void insertBatch(String userId, List<TelemetryEventDto> events) {
        if (!events.isEmpty()) repository.insertBatch(userId, events);
    }
}
