package com.example.layoff_tracker.model;

import java.time.Instant;
import java.util.List;

/** One complete public company/layoff document from Firestore, independent of API/UI projections. */
public record FeedCompany(
        String id, String companyName, Long impactCount, String status,
        String industry, String location, Instant reportedAt, String logoUrl, String trendDirection,
        String companyDescription, String layoffDetails,
        List<NewsSource> newsSources, List<Image> images, List<ArticleLink> articleLinks
) {
    public record NewsSource(String name, String url) {}
    public record Image(String url, String altText, String caption, String sourceUrl) {}
    public record ArticleLink(String url, String title, String sourceName, Instant publishedAt) {}

    public FeedCompany {
        newsSources = newsSources == null ? List.of() : List.copyOf(newsSources);
        images = images == null ? List.of() : List.copyOf(images);
        articleLinks = articleLinks == null ? List.of() : List.copyOf(articleLinks);
    }
}
