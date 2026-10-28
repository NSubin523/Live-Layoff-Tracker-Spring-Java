package com.example.layoff_tracker.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static com.example.layoff_tracker.analytics.AnalyticsDto.*;
import static com.example.layoff_tracker.analytics.CompanyAnalyticsRepository.CoverageState;

@Service
@Slf4j
public class CompanyAnalyticsService {
    private final CompanyAnalyticsRepository repository;
    private final Clock clock;
    @Autowired
    public CompanyAnalyticsService(CompanyAnalyticsRepository repository) { this(repository, Clock.systemUTC()); }
    CompanyAnalyticsService(CompanyAnalyticsRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    public Dashboard dashboard(String id, String from, String to) {
        AnalyticsQuery query = query(id, from, to);
        Company company = requireCompany(query);
        Instant now = clock.instant();
        Section<Workforce> workforce;
        try {
            var observation = repository.workforce(query.companyId(), LocalDate.now(clock));
            workforce = section(observation.orElse(null), repository.coverage(query.companyId(), "WORKFORCE"),
                    observation.isEmpty(), List.of(), now);
        } catch (RuntimeException e) {
            log.error("Workforce read failed for company {}", id, e);
            workforce = failed();
        }
        Section<Layoffs> layoffs;
        try {
            List<Bucket> buckets = completeBuckets(query, repository.monthlyBuckets(query));
            EventPage events = repository.events(query, null);
            long confirmed = buckets.stream().mapToLong(Bucket::confirmedEventCount).sum();
            long rumored = buckets.stream().mapToLong(Bucket::rumoredEventCount).sum();
            long unknown = buckets.stream().mapToLong(Bucket::unknownCountEventCount).sum();
            long ranged = buckets.stream().mapToLong(Bucket::rangedCountEventCount).sum();
            long overlaps = buckets.stream().mapToLong(Bucket::unresolvedOverlapEventCount).sum();
            var warnings = new ArrayList<String>();
            warnings.add("ANNOUNCEMENTS_ARE_NOT_COMPLETED_CUTS");
            if (unknown + ranged > 0) warnings.add("UNKNOWN_AND_RANGED_COUNTS_EXCLUDED_FROM_SUM");
            if (overlaps > 0) warnings.add("UNRESOLVED_OVERLAPS_EXCLUDED_FROM_SUM");
            var summary = new Layoffs(buckets.stream().mapToLong(Bucket::knownAffectedCount).sum(), confirmed,
                    unknown, ranged, overlaps, rumored, buckets, events.events(), events.nextCursor());
            layoffs = section(summary, repository.coverage(query.companyId(), "LAYOFFS"),
                    confirmed + rumored == 0, warnings, now);
        } catch (RuntimeException e) {
            log.error("Layoff read failed for company {}", id, e);
            layoffs = failed();
        }
        if (workforce.availability() == Availability.TEMPORARILY_UNAVAILABLE
                && layoffs.availability() == Availability.TEMPORARILY_UNAVAILABLE) throw unavailable();
        return new Dashboard(1, company, query.period(), now, workforce, layoffs);
    }

    public EventPage events(String id, String from, String to, String cursor) {
        AnalyticsQuery query = query(id, from, to);
        AnalyticsQuery.Position position = query.position(cursor);
        requireCompany(query);
        try { return repository.events(query, position); }
        catch (RuntimeException e) {
            log.error("Event page read failed for company {}", id, e);
            throw unavailable();
        }
    }

    private AnalyticsQuery query(String id, String from, String to) {
        return AnalyticsQuery.parse(id, from, to, LocalDate.now(clock));
    }
    private Company requireCompany(AnalyticsQuery query) {
        Optional<Company> company;
        try { company = repository.findCompany(query.companyId()); }
        catch (RuntimeException e) {
            log.error("Company identity read failed for {}", query.companyId(), e);
            throw unavailable();
        }
        return company.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    private static <T> Section<T> section(T data, CoverageState state, boolean empty, List<String> extraWarnings, Instant now) {
        List<String> warnings = new ArrayList<>(extraWarnings);
        warnings.add("COVERAGE_IS_INCOMPLETE");
        if ("FAILED".equals(state.lastAttemptOutcome())) warnings.add("LAST_REFRESH_FAILED");
        Freshness freshness = state.lastSuccessfulRefreshAt() == null || state.refreshDueAt() == null
                ? Freshness.UNKNOWN : (now.isBefore(state.refreshDueAt()) ? Freshness.FRESH : Freshness.STALE);
        return new Section<>(empty ? Availability.NO_DATA : Availability.PARTIAL, freshness, data,
                state.lastSuccessfulRefreshAt(), state.coverage(), empty ? "NO_RECORDED_DATA" : "INCOMPLETE_COVERAGE", List.copyOf(warnings));
    }
    private static <T> Section<T> failed() {
        return new Section<>(Availability.TEMPORARILY_UNAVAILABLE, Freshness.UNKNOWN, null, null,
                new Coverage(null, null, null, "UNKNOWN"), "STORAGE_UNAVAILABLE", List.of());
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Analytics temporarily unavailable");
    }
    private static List<Bucket> completeBuckets(AnalyticsQuery query, List<Bucket> existing) {
        Map<LocalDate, Bucket> byMonth = new HashMap<>();
        existing.forEach(bucket -> byMonth.put(bucket.from().withDayOfMonth(1), bucket));
        List<Bucket> buckets = new ArrayList<>();
        for (LocalDate month = query.from().withDayOfMonth(1); !month.isAfter(query.to()); month = month.plusMonths(1)) {
            LocalDate start = month.isBefore(query.from()) ? query.from() : month;
            LocalDate end = month.withDayOfMonth(month.lengthOfMonth());
            if (end.isAfter(query.to())) end = query.to();
            buckets.add(byMonth.getOrDefault(month, new Bucket(start, end, 0, 0, 0, 0, 0, 0)));
        }
        return List.copyOf(buckets);
    }
}
