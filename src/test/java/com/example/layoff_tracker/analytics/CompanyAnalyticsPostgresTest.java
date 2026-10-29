package com.example.layoff_tracker.analytics;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Creates and drops a dedicated database; never migrates the application database. */
@EnabledIfEnvironmentVariable(named = "ANALYTICS_TEST_DB_PASSWORD", matches = ".+")
class CompanyAnalyticsPostgresTest {
    static JdbcTemplate admin;
    static JdbcTemplate jdbc;
    static CompanyAnalyticsRepository repository;
    static String database;
    static boolean created;
    static final UUID COMPANY = UUID.fromString(AnalyticsQueryTest.ID);
    static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID SOURCE_2 = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @BeforeAll static void createDatabase() throws Exception {
        String url = System.getenv().getOrDefault("ANALYTICS_TEST_DB_URL", "jdbc:postgresql://localhost:55439/analytics_test");
        String username = System.getenv().getOrDefault("ANALYTICS_TEST_DB_USERNAME", "postgres");
        String password = System.getenv("ANALYTICS_TEST_DB_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        database = "analytics_it_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + database);
        created = true;
        URI base = URI.create(url.substring(5));
        String testUrl = "jdbc:" + new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(),
                "/" + database, base.getQuery(), null);
        var ds = new DriverManagerDataSource(testUrl, username, password);
        jdbc = new JdbcTemplate(ds);
        try (var connection = ds.getConnection()) {
            try (var statement = connection.createStatement()) {
                statement.execute(Files.readString(Path.of("db/migrations/V001__company_analytics.sql")));
            }
        }
        repository = new CompanyAnalyticsRepository(jdbc);
    }

    @AfterAll static void cleanup() {
        if (created) admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }

    @BeforeEach void seedSyntheticEvidence() {
        jdbc.execute("TRUNCATE company_analytics.companies, company_analytics.sources CASCADE");
        jdbc.execute("TRUNCATE company_analytics.layoff_event_revisions");
        jdbc.update("INSERT INTO company_analytics.companies(id,name,listing_status) VALUES (?, 'Synthetic Company', 'PRIVATE')", COMPANY);
        for (UUID id : List.of(SOURCE, SOURCE_2)) {
            jdbc.update("""
                    INSERT INTO company_analytics.sources(id,publisher,url,source_type,retrieved_at)
                    VALUES (?, 'Synthetic fixture', 'https://example.invalid/source', 'COMPANY_DISCLOSURE', '2026-10-01T00:00:00Z')
                    """, id);
        }
    }

    @Test void migrationEnforcesEvidenceCountsAndUniqueLegacyMapping() {
        jdbc.update("INSERT INTO company_analytics.company_legacy_refs VALUES ('firestore:layoffs','legacy',?)", COMPANY);
        assertEquals(COMPANY.toString(), repository.resolveLegacyIds(List.of("legacy", "unmapped")).get("legacy"));
        assertThrows(RuntimeException.class, () -> jdbc.update("INSERT INTO company_analytics.company_legacy_refs VALUES ('firestore:layoffs','legacy',?)", COMPANY));
        assertTrue(repository.resolveLegacyIds(List.of()).isEmpty());
        assertEquals("Synthetic Company", repository.findCompany(COMPANY).orElseThrow().name());
        assertTrue(repository.findCompany(UUID.randomUUID()).isEmpty());
        assertThrows(RuntimeException.class, () -> insertEvent(1, -1L, "CONFIRMED", "VERIFIED_DISTINCT", "ACTIVE"));
        insertEvent(1, 10L, "CONFIRMED", "VERIFIED_DISTINCT", "ACTIVE");
        assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE company_analytics.layoff_events SET primary_source_id=?", UUID.randomUUID()));
        assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE company_analytics.layoff_events SET range_min=1, range_max=2"));
        jdbc.update("UPDATE company_analytics.layoff_events SET provider='fixture',provider_record_id='same-event'");
        insertEvent(2, 20L, "CONFIRMED", "VERIFIED_DISTINCT", "ACTIVE");
        assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE company_analytics.layoff_events SET provider='fixture',provider_record_id='same-event' WHERE id=?", eventId(2)));
    }

    @Test void aggregatesOnlyVerifiedConfirmedExactCountsAndRetainsExclusions() {
        insertEvent(1, 100L, "CONFIRMED", "VERIFIED_DISTINCT", "ACTIVE");
        insertEvent(2, 900L, "RUMORED", "VERIFIED_DISTINCT", "ACTIVE");
        insertEvent(3, 700L, "CONFIRMED", "UNRESOLVED", "ACTIVE");
        insertEvent(4, null, "CONFIRMED", "VERIFIED_DISTINCT", "ACTIVE");
        insertEvent(5, null, "CONFIRMED", "VERIFIED_DISTINCT", "ACTIVE");
        jdbc.update("UPDATE company_analytics.layoff_events SET range_min=20,range_max=40 WHERE id=?", eventId(5));
        insertEvent(6, 800L, "CONFIRMED", "VERIFIED_DISTINCT", "CANCELLED");
        insertEvent(7, 800L, "CONFIRMED", "VERIFIED_DISTINCT", "SUPERSEDED");
        var bucket = repository.monthlyBuckets(query()).getFirst();
        assertEquals(100, bucket.knownAffectedCount());
        assertEquals(4, bucket.confirmedEventCount());
        assertEquals(1, bucket.unknownCountEventCount());
        assertEquals(1, bucket.rangedCountEventCount());
        assertEquals(1, bucket.unresolvedOverlapEventCount());
        assertEquals(1, bucket.rumoredEventCount());
        assertEquals(LocalDate.of(2026, 1, 15), bucket.from());
        assertEquals(5, repository.events(query(), null).events().size());
    }

    @Test void pagesTiedDatesWithoutDuplicatesAndHydratesEvidenceInBatches() {
        for (int i = 1; i <= 55; i++) insertEvent(i, 10L, "CONFIRMED", "VERIFIED_DISTINCT", "ACTIVE");
        // Repeating the primary source as supporting evidence must not duplicate it.
        jdbc.update("INSERT INTO company_analytics.layoff_event_sources VALUES (?, ?, 'primary again')", eventId(55), SOURCE);
        jdbc.update("INSERT INTO company_analytics.layoff_event_sources VALUES (?, ?, 'additional evidence')", eventId(55), SOURCE_2);
        var first = repository.events(query(), null);
        assertEquals(50, first.events().size());
        assertEquals(2, first.events().getFirst().sources().size());
        assertNotNull(first.nextCursor());
        var second = repository.events(query(), query().position(first.nextCursor()));
        assertEquals(5, second.events().size());
        assertNull(second.nextCursor());
        var ids = new HashSet<String>();
        first.events().forEach(event -> assertTrue(ids.add(event.id())));
        second.events().forEach(event -> assertTrue(ids.add(event.id())));
        assertEquals(55, ids.size());
        assertEquals(550, repository.monthlyBuckets(query()).getFirst().knownAffectedCount());
        assertEquals("https://example.invalid/source", second.events().getFirst().sources().getFirst().url());
    }

    @Test void workforcePrefersDatedReportedEvidenceOverNewerEstimatesAndIgnoresFutureFacts() {
        insertWorkforce(1, "2025-12-31", "COMPANY_REPORTED", 1000);
        insertWorkforce(2, "2026-09-30", "ESTIMATED", 1100);
        insertWorkforce(3, "2026-12-31", "COMPANY_REPORTED", 1200);
        insertWorkforce(4, "2026-10-01", "COMPANY_REPORTED", 200);
        jdbc.update("UPDATE company_analytics.workforce_observations SET geography='US' WHERE employee_count=200");
        var observation = repository.workforce(COMPANY, AnalyticsQueryTest.TODAY).orElseThrow();
        assertEquals(1000L, observation.employeeCount());
        assertEquals(LocalDate.of(2025, 12, 31), observation.asOf());
        assertEquals("COMPANY_REPORTED", observation.classification());
        assertEquals(1, observation.sources().size());
        jdbc.update("DELETE FROM company_analytics.workforce_observations WHERE classification='COMPANY_REPORTED'");
        assertEquals("ESTIMATED", repository.workforce(COMPANY, AnalyticsQueryTest.TODAY).orElseThrow().classification());
    }

    @Test void correctionsAndCancellationsRetainThePreviousClaim() {
        insertEvent(1, 100L, "CONFIRMED", "VERIFIED_DISTINCT", "ACTIVE");
        jdbc.update("UPDATE company_analytics.layoff_events SET affected_count=75 WHERE id=?", eventId(1));
        jdbc.update("UPDATE company_analytics.layoff_events SET record_state='CANCELLED' WHERE id=?", eventId(1));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM company_analytics.layoff_event_revisions", Integer.class));
        assertEquals(100L, jdbc.queryForObject("SELECT (snapshot->>'affected_count')::bigint FROM company_analytics.layoff_event_revisions ORDER BY revision_id LIMIT 1", Long.class));
        assertTrue(repository.events(query(), null).events().isEmpty());
        assertTrue(repository.monthlyBuckets(query()).isEmpty());
    }

    @Test void coverageStoresRefreshPolicyAndUnknownCoverageIsNotInvented() {
        assertNull(repository.coverage(COMPANY, "LAYOFFS").lastSuccessfulRefreshAt());
        jdbc.update("""
                INSERT INTO company_analytics.analytics_coverage(company_id,section,scope,completeness,
                    last_successful_refresh_at,refresh_due_at,last_attempt_outcome)
                VALUES (?, 'LAYOFFS', 'GLOBAL', 'PARTIAL', '2026-10-01T00:00:00Z','2026-10-02T00:00:00Z','FAILED')
                """, COMPANY);
        var coverage = repository.coverage(COMPANY, "LAYOFFS");
        assertEquals("PARTIAL", coverage.coverage().completeness());
        assertEquals("FAILED", coverage.lastAttemptOutcome());
        assertNotNull(coverage.refreshDueAt());
    }

    private static AnalyticsQuery query() {
        return AnalyticsQuery.parse(COMPANY.toString(), "2026-01-15", "2026-03-04", AnalyticsQueryTest.TODAY);
    }
    private static UUID eventId(int i) { return new UUID(0, 1000 + i); }
    private static void insertEvent(int i, Long count, String status, String overlap, String state) {
        jdbc.update("""
                INSERT INTO company_analytics.layoff_events(id,company_id,announced_on,affected_count,status,
                    overlap_state,record_state,geography,organizational_scope,employee_definition,primary_source_id)
                VALUES (?,?,'2026-01-20',?,?,?,?,'GLOBAL','CONSOLIDATED','EMPLOYEES',?)
                """, eventId(i), COMPANY, count, status, overlap, state, SOURCE);
    }
    private static void insertWorkforce(int i, String date, String classification, long count) {
        jdbc.update("""
                INSERT INTO company_analytics.workforce_observations(id,company_id,employee_count,as_of,
                    geography,organizational_scope,employee_definition,classification,source_id)
                VALUES (?,?,?,?::date,'GLOBAL','CONSOLIDATED','EMPLOYEES',?,?)
                """, new UUID(0, 2000 + i), COMPANY, count, date, classification, SOURCE);
    }
}
