package com.example.layoff_tracker.analytics;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/** Validated date windows and a bounded, company/period-bound keyset cursor. */
public record AnalyticsQuery(UUID companyId, LocalDate from, LocalDate to) {
    public static AnalyticsQuery parse(String id, String from, String to, LocalDate today) {
        UUID company;
        try { company = canonicalUuid(id); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"); }
        if ((from == null) != (to == null)) throw invalid("Supply both from and to, or neither");
        try {
            LocalDate end = to == null ? today : date(to);
            LocalDate start = from == null ? end.minusMonths(12).plusDays(1) : date(from);
            if (start.isAfter(end) || end.isAfter(today) || start.isBefore(end.minusYears(5))) {
                throw invalid("Invalid date range: maximum five years, with no future end date");
            }
            return new AnalyticsQuery(company, start, end);
        } catch (DateTimeParseException e) { throw invalid("Dates must use YYYY-MM-DD"); }
    }

    public AnalyticsDto.Period period() { return new AnalyticsDto.Period(from, to, "ANNOUNCEMENT_DATE"); }

    public record Position(LocalDate date, UUID id) {}

    public String cursor(AnalyticsDto.Event event) {
        String raw = "v1|" + companyId + "|" + from + "|" + to + "|" + event.announcedOn() + "|" + event.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public Position position(String cursor) {
        if (cursor == null) return null;
        try {
            if (cursor.length() > 512) throw new IllegalArgumentException();
            String[] fields = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 6 || !fields[0].equals("v1") || !fields[1].equals(companyId.toString())
                    || !fields[2].equals(from.toString()) || !fields[3].equals(to.toString())) throw new IllegalArgumentException();
            LocalDate date = LocalDate.parse(fields[4]);
            if (date.isBefore(from) || date.isAfter(to)) throw new IllegalArgumentException();
            return new Position(date, canonicalUuid(fields[5]));
        } catch (IllegalArgumentException | DateTimeParseException e) { throw invalid("Invalid cursor for company and period"); }
    }

    private static UUID canonicalUuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        return id;
    }
    private static LocalDate date(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000-")) {
            throw invalid("Dates must use YYYY-MM-DD with a positive four-digit year");
        }
        return LocalDate.parse(value);
    }
    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
