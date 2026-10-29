package com.example.layoff_tracker.dto;

import com.example.layoff_tracker.model.FeedCompany;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** Complete public payload shared by the feed list and detail fallback. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FeedResponseDto(
        String id, String companyName, Long impactCount, String status,
        String industry, String location, String reportedAt, String logoUrl, String trendDirection,
        String companyDescription, String layoffDetails,
        List<NewsSource> newsSources, List<Image> images, List<ArticleLink> articleLinks
) {
    public record NewsSource(String name, String url) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Image(String url, String altText, String caption, String sourceUrl) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ArticleLink(String url, String title, String sourceName, String publishedAt) {}

    public FeedResponseDto {
        newsSources = newsSources == null ? List.of() : List.copyOf(newsSources);
        images = images == null ? List.of() : List.copyOf(images);
        articleLinks = articleLinks == null ? List.of() : List.copyOf(articleLinks);
    }

    public FeedResponseDto(String id, String companyName, Long impactCount, String status,
                           String industry, String location, String reportedAt, String logoUrl, String trendDirection) {
        this(id, companyName, impactCount, status, industry, location, reportedAt, logoUrl, trendDirection,
                null, null, List.of(), List.of(), List.of());
    }

    public static FeedResponseDto from(FeedCompany company) {
        return new FeedResponseDto(company.id(), company.companyName(), company.impactCount(), company.status(),
                company.industry(), company.location(), company.reportedAt().toString(), company.logoUrl(), company.trendDirection(),
                company.companyDescription(), company.layoffDetails(),
                company.newsSources().stream().map(s -> new NewsSource(s.name(), s.url())).toList(),
                company.images().stream().map(i -> new Image(i.url(), i.altText(), i.caption(), i.sourceUrl())).toList(),
                company.articleLinks().stream().map(a -> new ArticleLink(a.url(), a.title(), a.sourceName(),
                        a.publishedAt() == null ? null : a.publishedAt().toString())).toList());
    }
}
