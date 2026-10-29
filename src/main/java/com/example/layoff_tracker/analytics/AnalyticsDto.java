package com.example.layoff_tracker.analytics;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Public analytics wire contract. Existing feed and favorites casing is unchanged. */
public final class AnalyticsDto {
    private AnalyticsDto() {}

    public enum Availability { AVAILABLE, PARTIAL, NO_DATA, UNSUPPORTED, TEMPORARILY_UNAVAILABLE }
    public enum Freshness { FRESH, STALE, UNKNOWN }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Company(String id, String name, String logoUrl, String listingStatus) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Period(LocalDate from, LocalDate to, String eventDateBasis) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Coverage(LocalDate from, LocalDate to, String scope, String completeness) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Section<T>(Availability availability, Freshness freshness, T data,
                             Instant lastSuccessfulRefreshAt, Coverage coverage,
                             String reasonCode, List<String> warnings) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Source(String id, String publisher, String url, String sourceType,
                         LocalDate publishedOn, Instant retrievedAt) {}
    public record Scope(String geography, String organization, String employees) {}
    public record CountRange(Long min, Long max) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Workforce(String id, Long employeeCount, CountRange employeeRange,
                            LocalDate asOf, String classification, Scope scope, List<Source> sources) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Event(String id, LocalDate announcedOn, LocalDate effectiveFrom, LocalDate effectiveTo,
                        Long affectedCount, CountRange affectedRange, String status, String executionState,
                        String overlapState, Scope scope, List<Source> sources) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Bucket(LocalDate from, LocalDate to, long knownAffectedCount,
                         long confirmedEventCount, long unknownCountEventCount,
                         long rangedCountEventCount, long unresolvedOverlapEventCount, long rumoredEventCount) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record EventPage(String companyId, Period period, List<Event> events, String nextCursor) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Layoffs(long reportedAffectedCount, long confirmedEventCount,
                          long unknownCountEventCount, long rangedCountEventCount,
                          long unresolvedOverlapEventCount, long rumoredEventCount,
                          List<Bucket> monthlyBuckets, List<Event> events, String nextCursor) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Dashboard(int schemaVersion, Company company, Period period, Instant generatedAt,
                            Section<Workforce> workforce, Section<Layoffs> layoffs) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Error(String code, String detail, String requestId) {}
}
