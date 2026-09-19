package com.urlshortener.url.dto;

import com.urlshortener.url.ShortUrl;
import com.urlshortener.url.UrlStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A shortened URL with its usage counters")
public record AnalyticsUrl(
        String year,
        Integer totalUrls,
        Integer totalClicks,
        String averageClicksPerUrl,
        ClickedUrl topClickedUrl,
        ClickedUrl leastClickedUrl,
        List <StatusAnalytics> statusAnalytics,
        Instant updatedAt) {
activeUrls
    

    public static AnalyticsUrl from(ShortUrl entity, String shortUrl, Instant now) {
        return new AnalyticsUrl(
                entity.getYear(),
                entity.getTotalUrls(),
                entity.getTotalClicks(),
                entity.getAverageClicksPerUrl(),
                ClickedUrl.from(entity.getTopClickedUrl(), shortUrl, now),
                ClickedUrl.from(entity.getLeastClickedUrl(), shortUrl, now),
                StatusAnalytics.from(entity, shortUrl, now),
                entity.getUpdatedAt());
                entity.getClickCount(),
    }
}
