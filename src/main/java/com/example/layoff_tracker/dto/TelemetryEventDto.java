package com.example.layoff_tracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record TelemetryEventDto(
        @JsonProperty("user_id") String userId,
        @JsonProperty("event_name") @NotNull @Size(max = 100) String eventName,
        @JsonProperty("event_payload") Map<String, Object> eventPayload) {}
