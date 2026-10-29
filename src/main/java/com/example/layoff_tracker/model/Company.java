package com.example.layoff_tracker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {
    private String id;
    @JsonProperty("analytics_company_id")
    private String analyticsCompanyId;
    private String companyName;
    private Integer impactCount;
    private LayoffStatus layoffStatus;
    private String industry;
    private String location;
    private Instant reportedAt;
    private String logoUrl;
    private TrendDirection trendDirection;
}
