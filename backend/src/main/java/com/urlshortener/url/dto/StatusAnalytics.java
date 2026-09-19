package com.urlshortener.url.dto;

import com.urlshortener.url.ShortUrl;
import com.urlshortener.url.UrlStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A shortened URL with its usage counters")
public record StatusAnalytics(
        UrlStatus status,
        Integer urlCount,
        Integer totalClicks,
        Integer averageClicksPerUrl,
        ) {

    

    public static StatusAnalytics from(ShortUrl entity, String shortUrl, Instant now) {
        return new StatusAnalytics(
                entity.getStatus(),
                entity.getUrlCount(),
                entity.getTotalClicks(),
                entity.getAverageClicksPerUrl()
        );
    }

}
