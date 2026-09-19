package com.urlshortener.url.dto;

import com.urlshortener.url.ShortUrl;
import com.urlshortener.url.UrlStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A shortened URL with its usage counters")
public record ClickedUrl(
        String shortCode,
        String originalUrl,
        Integer clicks,
        ) {

    

    public static ClickedUrl from(ShortUrl entity, String shortUrl, Instant now) {
        return new ClickedUrl(
                entity.getShortCode(),
                entity.getOriginalUrl(),
                entity.getClickCount()
        );
    }

}
