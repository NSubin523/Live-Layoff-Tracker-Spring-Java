package com.example.layoff_tracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Explicit wire contract shared with the existing Android feed client. */
public record FeedResponseDto(
        String id,
        @JsonProperty("company_name") String companyName,
        @JsonProperty("impact_count") Long impactCount,
        String status,
        String industry,
        String location,
        @JsonProperty("reported_at") String reportedAt,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("trend_direction") String trendDirection
) {}
