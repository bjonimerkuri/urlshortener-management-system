package com.urlshortener.url;

import com.urlshortener.common.PageResponse;
import com.urlshortener.security.AuthenticatedUser;
import com.urlshortener.url.dto.CreateShortUrlRequest;
import com.urlshortener.url.dto.ShortUrlResponse;
import com.urlshortener.url.dto.UpdateShortUrlRequest;
import com.urlshortener.url.dto.UrlStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "Short URLs", description = "Manage your short URLs")
public class ShortUrlController {

    private final ShortUrlService shortUrlService;

    public ShortUrlController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @PostMapping
    @Operation(
            summary = "Shorten a URL",
            description = "Supply an optional custom alias, otherwise a random code is generated. "
                    + "Omitting the expiration date applies the configured default lifetime.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Short URL created"),
        @ApiResponse(responseCode = "400", description = "Invalid URL, alias or expiration date", content = @Content),
        @ApiResponse(responseCode = "409", description = "Alias already in use", content = @Content)
    })
    public ResponseEntity<ShortUrlResponse> create(
            AuthenticatedUser caller, @Valid @RequestBody CreateShortUrlRequest request) {
        ShortUrlResponse created = shortUrlService.create(caller, request);
        return ResponseEntity.created(URI.create("/api/v1/urls/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(
            summary = "List your short URLs",
            description = "Supports free-text search, status and creation-date filters, sorting and pagination. "
                    + "Sort with `sort=clickCount,desc`; allowed properties are createdAt, updatedAt, shortCode, "
                    + "originalUrl, clickCount, expiresAt, lastAccessedAt and status.")
    public PageResponse<ShortUrlResponse> list(
            AuthenticatedUser caller,
            @ParameterObject UrlSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return shortUrlService.search(caller, criteria, pageable);
    }

    @GetMapping("/stats")
    @Operation(summary = "Dashboard counters for your URLs: totals, active, inactive, expired and clicks")
    public UrlStatsResponse stats(AuthenticatedUser caller) {
        return shortUrlService.statsFor(caller);
    }

    @GetMapping("/analytics/yearly")
    @Operation(summary = "Dashboard counters for your URLs: totals, active, inactive, expired and clicks")
    public UrlStatsResponse analytics(@RequestParam(required = true) String year, AuthenticatedUser caller) {
        return shortUrlService.analytics(caller);
    }


    @GetMapping("/{id}")
    @Operation(summary = "Fetch one of your short URLs")
    @ApiResponse(responseCode = "404", description = "Not found, or not yours", content = @Content)
    public ShortUrlResponse get(AuthenticatedUser caller, @PathVariable UUID id) {
        return shortUrlService.get(caller, id);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update the expiration date and activation state",
            description = "A null expiration date means the link never expires. A date in the past expires it at once.")
    public ShortUrlResponse update(
            AuthenticatedUser caller, @PathVariable UUID id, @Valid @RequestBody UpdateShortUrlRequest request) {
        return shortUrlService.update(caller, id, request);
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a short URL")
    @ApiResponse(responseCode = "400", description = "The link has expired and cannot be activated", content = @Content)
    public ShortUrlResponse activate(AuthenticatedUser caller, @PathVariable UUID id) {
        return shortUrlService.setActive(caller, id, true);
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a short URL so it stops resolving")
    public ShortUrlResponse deactivate(AuthenticatedUser caller, @PathVariable UUID id) {
        return shortUrlService.setActive(caller, id, false);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a short URL permanently")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<Void> delete(AuthenticatedUser caller, @PathVariable UUID id) {
        shortUrlService.delete(caller, id);
        return ResponseEntity.noContent().build();
    }
}
