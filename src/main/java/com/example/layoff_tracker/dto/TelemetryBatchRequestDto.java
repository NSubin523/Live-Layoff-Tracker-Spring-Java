package com.example.layoff_tracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TelemetryBatchRequestDto(@NotNull List<@NotNull @Valid TelemetryEventDto> events) {}
