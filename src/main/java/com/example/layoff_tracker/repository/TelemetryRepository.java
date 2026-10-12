package com.example.layoff_tracker.repository;

import com.example.layoff_tracker.dto.TelemetryEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TelemetryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public void insertBatch(String userId, List<TelemetryEventDto> events) {
        jdbc.batchUpdate("INSERT INTO telemetry (user_id, event_name, event_payload) VALUES (?, ?, CAST(? AS jsonb))",
                events, 100, (statement, event) -> {
                    statement.setString(1, userId);
                    statement.setString(2, event.eventName());
                    statement.setString(3, event.eventPayload() == null ? null : mapper.writeValueAsString(event.eventPayload()));
                });
    }
}
