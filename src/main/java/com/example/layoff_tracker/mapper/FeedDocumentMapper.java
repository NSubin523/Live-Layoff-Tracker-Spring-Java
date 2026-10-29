package com.example.layoff_tracker.mapper;

import com.example.layoff_tracker.model.FeedCompany;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Explicit allowlist of public detail fields; raw ingestion metadata stays private. */
public final class FeedDocumentMapper {
    private FeedDocumentMapper() {}

    public static FeedCompany fromDocument(DocumentSnapshot doc) {
        Timestamp reportedAt = doc.getTimestamp("reported_at");
        if (reportedAt == null) throw invalid("Feed record is missing reported_at");
        return new FeedCompany(doc.getId(), doc.getString("company_name"), doc.getLong("impact_count"), doc.getString("status"),
                doc.getString("industry"), doc.getString("location"), instant(reportedAt), doc.getString("logo_url"), doc.getString("trend_direction"),
                doc.getString("company_description"), doc.getString("layoff_details"),
                entries(doc.get("news_sources"), source -> new FeedCompany.NewsSource(
                        requiredText(source, "name"), url(source, "url", false))),
                entries(doc.get("images"), image -> new FeedCompany.Image(
                        url(image, "url", true), text(image, "alt_text"), text(image, "caption"), url(image, "source_url", false))),
                entries(doc.get("article_links"), article -> new FeedCompany.ArticleLink(
                        url(article, "url", true), text(article, "title"), text(article, "source_name"), publishedAt(article))));
    }

    private static <T> List<T> entries(Object raw, Function<Map<?, ?>, T> mapper) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) throw invalid("Detail collections must be arrays");
        return list.stream().map(entry -> {
            if (!(entry instanceof Map<?, ?> fields)) throw invalid("Detail array entries must be objects");
            return mapper.apply(fields);
        }).toList();
    }

    private static String text(Map<?, ?> fields, String key) {
        Object value = fields.get(key);
        if (value == null) return null;
        if (!(value instanceof String string)) throw invalid("Detail text must be a string");
        return string;
    }
    private static String requiredText(Map<?, ?> fields, String key) {
        String value = text(fields, key);
        if (value == null || value.isBlank()) throw invalid("Required detail text is missing");
        return value;
    }
    private static String url(Map<?, ?> fields, String key, boolean required) {
        String value = required ? requiredText(fields, key) : text(fields, key);
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw invalid("Detail links must be absolute HTTP(S) URLs"); }
        return value;
    }
    private static Instant publishedAt(Map<?, ?> fields) {
        Object value = fields.get("published_at");
        if (value == null) return null;
        if (!(value instanceof Timestamp timestamp)) throw invalid("Article published_at must be a Firestore timestamp");
        return instant(timestamp);
    }
    private static Instant instant(Timestamp timestamp) { return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()); }
    private static IllegalStateException invalid(String reason) { return new IllegalStateException(reason); }
}
