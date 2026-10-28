package com.example.layoff_tracker.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticsQueryTest {
    static final String ID = "00000000-0000-0000-0000-000000000001";
    static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test void defaultPeriodAndLeapYearBoundariesAreDeterministic() {
        var query = AnalyticsQuery.parse(ID, null, null, TODAY);
        assertEquals(LocalDate.of(2025, 10, 29), query.from());
        assertEquals(TODAY, query.to());
        assertDoesNotThrow(() -> AnalyticsQuery.parse(ID, "2024-02-29", "2024-03-01", TODAY));
    }
    @Test void rejectsInvalidRangesAndDateFormats() {
        for (String[] range : List.of(new String[]{"2026-01-01", null}, new String[]{null, "2026-01-01"},
                new String[]{"2026-02-30", "2026-03-01"}, new String[]{"2026-02-01", "2026-01-01"},
                new String[]{"2026-10-01", "2026-10-29"}, new String[]{"2020-01-01", "2026-01-01"},
                new String[]{"-999999999-01-01", "-999999999-01-01"}, new String[]{"0000-01-01", "0000-01-02"})) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> AnalyticsQuery.parse(ID, range[0], range[1], TODAY)).getStatusCode().value());
        }
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> AnalyticsQuery.parse("legacy-id", null, null, TODAY)).getStatusCode().value());
    }
    @Test void cursorIsBoundToCompanyAndExactPeriod() {
        var query = AnalyticsQuery.parse(ID, "2026-01-01", "2026-10-28", TODAY);
        var event = new AnalyticsDto.Event(ID, LocalDate.of(2026, 4, 1), null, null,
                20L, null, "CONFIRMED", "PLANNED", "VERIFIED_DISTINCT", null, List.of());
        String cursor = query.cursor(event);
        assertEquals(event.announcedOn(), query.position(cursor).date());
        assertThrows(ResponseStatusException.class, () -> AnalyticsQuery.parse(ID, "2026-02-01", "2026-10-28", TODAY).position(cursor));
        assertThrows(ResponseStatusException.class, () -> AnalyticsQuery.parse("00000000-0000-0000-0000-000000000002", "2026-01-01", "2026-10-28", TODAY).position(cursor));
        assertThrows(ResponseStatusException.class, () -> query.position("invalid"));
        assertThrows(ResponseStatusException.class, () -> query.position("x".repeat(513)));
    }
}
