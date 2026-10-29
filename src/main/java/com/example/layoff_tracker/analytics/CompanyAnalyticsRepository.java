package com.example.layoff_tracker.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static com.example.layoff_tracker.analytics.AnalyticsDto.*;

@Repository
public class CompanyAnalyticsRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public CompanyAnalyticsRepository(JdbcTemplate jdbc) { this.jdbc = new NamedParameterJdbcTemplate(jdbc); }

    public Optional<Company> findCompany(UUID id) {
        return jdbc.query("SELECT id, name, logo_url, listing_status FROM company_analytics.companies WHERE id=:id",
                Map.of("id", id), (rs, n) -> new Company(rs.getString("id"), rs.getString("name"),
                        rs.getString("logo_url"), rs.getString("listing_status"))).stream().findFirst();
    }

    /** Batch resolution only; no company-name guessing or Firestore writes. */
    public Map<String, String> resolveLegacyIds(List<String> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<String, String> resolved = new HashMap<>();
        for (int offset = 0; offset < ids.size(); offset += 500) {
            jdbc.query("""
                    SELECT legacy_id, company_id FROM company_analytics.company_legacy_refs
                    WHERE namespace='firestore:layoffs' AND legacy_id IN (:ids)
                    """, Map.of("ids", ids.subList(offset, Math.min(offset + 500, ids.size()))), rs -> {
                resolved.put(rs.getString("legacy_id"), rs.getString("company_id"));
            });
        }
        return resolved;
    }

    public record CoverageState(Coverage coverage, Instant lastSuccessfulRefreshAt,
                                Instant refreshDueAt, String lastAttemptOutcome) {}

    public CoverageState coverage(UUID id, String section) {
        return jdbc.query("""
                SELECT * FROM company_analytics.analytics_coverage WHERE company_id=:id AND section=:section
                """, Map.of("id", id, "section", section), (rs, n) -> new CoverageState(
                        new Coverage(date(rs, "coverage_from"), date(rs, "coverage_to"), rs.getString("scope"),
                                rs.getString("completeness")), instant(rs, "last_successful_refresh_at"),
                        instant(rs, "refresh_due_at"), rs.getString("last_attempt_outcome")))
                .stream().findFirst().orElse(new CoverageState(new Coverage(null, null, null, "UNKNOWN"), null, null, null));
    }

    /** Prefer company-reported evidence. A fallback estimate retains its classification. */
    public Optional<Workforce> workforce(UUID id, LocalDate today) {
        return jdbc.query("""
                SELECT w.*, s.id AS evidence_id, s.publisher, s.url, s.source_type, s.published_on, s.retrieved_at
                FROM company_analytics.workforce_observations w
                JOIN company_analytics.sources s ON s.id=w.source_id
                WHERE company_id=:id AND as_of<=:today
                  AND geography='GLOBAL' AND organizational_scope='CONSOLIDATED' AND employee_definition='EMPLOYEES'
                ORDER BY CASE classification WHEN 'COMPANY_REPORTED' THEN 0 ELSE 1 END, as_of DESC, w.id DESC LIMIT 1
                """, Map.of("id", id, "today", today), (rs, n) -> new Workforce(rs.getString("id"),
                        nullableLong(rs, "employee_count"), range(rs), date(rs, "as_of"), rs.getString("classification"),
                        scope(rs), List.of(source(rs)))).stream().findFirst();
    }

    public List<Bucket> monthlyBuckets(AnalyticsQuery query) {
        // A verified distinct exact count is the only input to the known-count sum.
        // Unknown/ranged/overlapping events remain visible in separate counters.
        return jdbc.query("""
                SELECT date_trunc('month', announced_on)::date AS month,
                  coalesce(sum(affected_count) FILTER (WHERE status='CONFIRMED' AND overlap_state='VERIFIED_DISTINCT'),0) AS known_count,
                  count(*) FILTER (WHERE status='CONFIRMED') AS confirmed,
                  count(*) FILTER (WHERE status='CONFIRMED' AND affected_count IS NULL AND range_min IS NULL) AS unknown_count,
                  count(*) FILTER (WHERE status='CONFIRMED' AND range_min IS NOT NULL) AS ranged_count,
                  count(*) FILTER (WHERE status='CONFIRMED' AND overlap_state='UNRESOLVED') AS unresolved,
                  count(*) FILTER (WHERE status='RUMORED') AS rumored
                FROM company_analytics.layoff_events
                WHERE company_id=:id AND announced_on BETWEEN :from AND :to AND record_state='ACTIVE'
                GROUP BY month ORDER BY month
                """, parameters(query), (rs, n) -> {
            LocalDate month = date(rs, "month");
            return new Bucket(month.isBefore(query.from()) ? query.from() : month,
                    month.withDayOfMonth(month.lengthOfMonth()).isAfter(query.to()) ? query.to() : month.withDayOfMonth(month.lengthOfMonth()),
                    rs.getLong("known_count"), rs.getLong("confirmed"), rs.getLong("unknown_count"),
                    rs.getLong("ranged_count"), rs.getLong("unresolved"), rs.getLong("rumored"));
        });
    }

    public EventPage events(AnalyticsQuery query, AnalyticsQuery.Position position) {
        Map<String, Object> parameters = parameters(query);
        String boundary = "";
        if (position != null) {
            boundary = " AND (announced_on, id) < (:cursor_date, :cursor_id)";
            parameters.put("cursor_date", position.date());
            parameters.put("cursor_id", position.id());
        }
        List<Event> fetched = jdbc.query("""
                SELECT * FROM company_analytics.layoff_events
                WHERE company_id=:id AND announced_on BETWEEN :from AND :to AND record_state='ACTIVE'
                """ + boundary + " ORDER BY announced_on DESC, id DESC LIMIT 51", parameters, eventMapper());
        boolean more = fetched.size() > 50;
        List<Event> page = fetched.subList(0, Math.min(50, fetched.size()));
        Map<String, List<Source>> sources = sourcesForEvents(page);
        List<Event> hydrated = page.stream().map(e -> new Event(e.id(), e.announcedOn(), e.effectiveFrom(), e.effectiveTo(),
                e.affectedCount(), e.affectedRange(), e.status(), e.executionState(), e.overlapState(), e.scope(),
                List.copyOf(sources.getOrDefault(e.id(), List.of())))).toList();
        return new EventPage(query.companyId().toString(), query.period(), hydrated,
                more ? query.cursor(hydrated.getLast()) : null);
    }

    private Map<String, List<Source>> sourcesForEvents(List<Event> events) {
        if (events.isEmpty()) return Map.of();
        List<UUID> ids = events.stream().map(e -> UUID.fromString(e.id())).toList();
        Map<String, List<Source>> result = new HashMap<>();
        jdbc.query("""
                SELECT links.event_id, s.id AS evidence_id, s.publisher, s.url, s.source_type, s.published_on, s.retrieved_at
                FROM (
                    SELECT id AS event_id, primary_source_id AS source_id FROM company_analytics.layoff_events WHERE id IN (:ids)
                    UNION
                    SELECT event_id, source_id FROM company_analytics.layoff_event_sources WHERE event_id IN (:ids)
                ) links JOIN company_analytics.sources s ON s.id=links.source_id
                ORDER BY links.event_id, s.id
                """, Map.of("ids", ids), rs -> {
            result.computeIfAbsent(rs.getString("event_id"), key -> new ArrayList<>()).add(source(rs));
        });
        return result;
    }

    private static RowMapper<Event> eventMapper() {
        return (rs, n) -> new Event(rs.getString("id"), date(rs, "announced_on"), date(rs, "effective_from"),
                date(rs, "effective_to"), nullableLong(rs, "affected_count"), range(rs), rs.getString("status"),
                rs.getString("execution_state"), rs.getString("overlap_state"), scope(rs), List.of());
    }
    private static Map<String, Object> parameters(AnalyticsQuery query) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", query.companyId()); params.put("from", query.from()); params.put("to", query.to());
        return params;
    }
    private static Source source(ResultSet rs) throws SQLException {
        return new Source(rs.getString("evidence_id"), rs.getString("publisher"), rs.getString("url"),
                rs.getString("source_type"), date(rs, "published_on"), instant(rs, "retrieved_at"));
    }
    private static Scope scope(ResultSet rs) throws SQLException {
        return new Scope(rs.getString("geography"), rs.getString("organizational_scope"), rs.getString("employee_definition"));
    }
    private static CountRange range(ResultSet rs) throws SQLException {
        Long min = nullableLong(rs, "range_min");
        return min == null ? null : new CountRange(min, nullableLong(rs, "range_max"));
    }
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, Long.class);
    }
    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
