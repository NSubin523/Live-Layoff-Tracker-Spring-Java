package com.example.layoff_tracker.repository;

import com.example.layoff_tracker.dto.TelemetryEventDto;
import com.example.layoff_tracker.service.TelemetryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_DB_PASSWORD", matches=".+")
class TelemetryPostgresTest {
    static String schema;
    static JdbcTemplate admin;
    static AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    TelemetryService service;
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/layoff_tracker_db")+"?currentSchema="+schema,
                System.getenv().getOrDefault("TEST_DB_USERNAME","postgres"),System.getenv("TEST_DB_PASSWORD"));
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager tx(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean TelemetryRepository repository(JdbcTemplate jdbc) { return new TelemetryRepository(jdbc,new ObjectMapper()); }
        @Bean TelemetryService service(TelemetryRepository r) { return new TelemetryService(r); }
    }
    @BeforeAll static void createIsolatedSchema() {
        schema="telemetry_test_"+UUID.randomUUID().toString().replace("-","");
        admin=new JdbcTemplate(new DriverManagerDataSource(System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/layoff_tracker_db"),
            System.getenv().getOrDefault("TEST_DB_USERNAME","postgres"),System.getenv("TEST_DB_PASSWORD")));
        admin.execute("CREATE SCHEMA "+schema);
        context=new AnnotationConfigApplicationContext(Config.class);
        context.getBean(JdbcTemplate.class).execute("CREATE TABLE telemetry (telemetry_id BIGSERIAL PRIMARY KEY, user_id VARCHAR(255) NOT NULL, event_name VARCHAR(100) NOT NULL, event_payload JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT now())");
    }
    @AfterAll static void cleanup() {
        if(context!=null) context.close();
        if(admin!=null && schema!=null) admin.execute("DROP SCHEMA "+schema+" CASCADE");
    }
    @BeforeEach void prepare() {
        jdbc=context.getBean(JdbcTemplate.class);service=context.getBean(TelemetryService.class);
        jdbc.execute("TRUNCATE telemetry");
    }
    @Test void persistsNestedJsonAndDatabaseTimestampWithServerIdentity() {
        service.insertBatch("verified",List.of(new TelemetryEventDto("spoofed","click",Map.of("nested",Map.of("count",2),"flags",List.of(true,false)))));
        assertEquals("verified",jdbc.queryForObject("SELECT user_id FROM telemetry",String.class));
        assertEquals(2,jdbc.queryForObject("SELECT (event_payload->'nested'->>'count')::int FROM telemetry",Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT created_at FROM telemetry",java.sql.Timestamp.class));
    }
    @Test void guestAndNullPayloadAreSupported() {
        service.insertBatch("Non-User",List.of(new TelemetryEventDto("spoofed","launch",null)));
        assertEquals("Non-User",jdbc.queryForObject("SELECT user_id FROM telemetry",String.class));
        assertNull(jdbc.queryForObject("SELECT event_payload FROM telemetry",String.class));
    }
    @Test void failedLaterChunkRollsBackEarlierChunk() {
        var events=new ArrayList<TelemetryEventDto>();
        for(int i=0;i<100;i++) events.add(new TelemetryEventDto("spoofed","valid",null));
        events.add(new TelemetryEventDto("spoofed","x".repeat(101),null));
        assertThrows(RuntimeException.class,()->service.insertBatch("verified",events));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM telemetry",Integer.class));
    }
}
