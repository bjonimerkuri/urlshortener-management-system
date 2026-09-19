package com.urlshortener.url;

import com.urlshortener.url.dto.UrlStatsResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, UUID>, JpaSpecificationExecutor<ShortUrl> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    Optional<ShortUrl> findByIdAndOwnerId(UUID id, UUID ownerId);

    

    @Modifying(clearAutomatically = true)
    @Query("update ShortUrl s set s.clickCount = s.clickCount + 1, s.lastAccessedAt = :now where s.id = :id")
    int registerClick(@Param("id") UUID id, @Param("now") Instant now);

    
    @Modifying(clearAutomatically = true)
    @Query(
            """
            update ShortUrl s
               set s.status = com.urlshortener.url.UrlStatus.EXPIRED
             where s.status <> com.urlshortener.url.UrlStatus.EXPIRED
               and s.expiresAt is not null
               and s.expiresAt <= :now
            """)
    int markExpired(@Param("now") Instant now);

    
    @Query(
            """
            select s.id from ShortUrl s
             where s.status = com.urlshortener.url.UrlStatus.EXPIRED
               and s.expiresAt is not null
               and s.expiresAt <= :cutoff
            """)
    List<UUID> findPurgeCandidates(@Param("cutoff") Instant cutoff, Limit limit);

    

    @Query(
            """
            select new com.urlshortener.url.dto.UrlStatsResponse(
                     count(s),
                     coalesce(sum(case when s.status = com.urlshortener.url.UrlStatus.INACTIVE then 0L
                                       when s.expiresAt is null or s.expiresAt > :now then 1L
                                       else 0L end), 0L),
                     coalesce(sum(case when s.status = com.urlshortener.url.UrlStatus.INACTIVE then 1L
                                       else 0L end), 0L),
                     coalesce(sum(case when s.status = com.urlshortener.url.UrlStatus.INACTIVE then 0L
                                       when s.expiresAt is not null and s.expiresAt <= :now then 1L
                                       else 0L end), 0L),
                     coalesce(sum(s.clickCount), 0L))
              from ShortUrl s
             where s.owner.id = :ownerId
            """)
    UrlStatsResponse statsForOwner(@Param("ownerId") UUID ownerId, @Param("now") Instant now);

    
    @Query(
            """
            select new com.urlshortener.url.dto.UrlStatsResponse(
                     count(s),
                     coalesce(sum(case when s.status = com.urlshortener.url.UrlStatus.INACTIVE then 0L
                                       when s.expiresAt is null or s.expiresAt > :now then 1L
                                       else 0L end), 0L),
                     coalesce(sum(case when s.status = com.urlshortener.url.UrlStatus.INACTIVE then 1L
                                       else 0L end), 0L),
                     coalesce(sum(case when s.status = com.urlshortener.url.UrlStatus.INACTIVE then 0L
                                       when s.expiresAt is not null and s.expiresAt <= :now then 1L
                                       else 0L end), 0L),
                     coalesce(sum(s.clickCount), 0L))
              from ShortUrl s
            """)
    UrlStatsResponse statsForAllOwners(@Param("now") Instant now);

    @Query(
        """
        select new com.urlshortener.url.dto.AnalyticsUrl(
            s.year,
            count(s),
            coalesce(sum(s.clickCount), 0L),
            coalesce(avg(s.clickCount), 0.0),
            (select new com.urlshortener.url.dto.ClickedUrl(
                top.shortCode,
                top.originalUrl,
                top.clickCount)
             from ShortUrl top
             where top.owner.id = :ownerId
             order by top.clickCount desc
             limit 1),
            (select new com.urlshortener.url.dto.ClickedUrl(
                least.shortCode,
                least.originalUrl,
                least.clickCount)
             from ShortUrl least
             where least.owner.id = :ownerId
             order by least.clickCount asc
             limit 1),
            (select new com.urlshortener.url.dto.StatusAnalytics(
                s.status,:now
                count(s),
                coalesce(sum(s.clickCount), 0L),
                coalesce(avg(s.clickCount), 0.0))
             from ShortUrl s
             where s.owner.id = :ownerId
             group by s.status),
            max(s.updatedAt))
    )
    """)
    UrlStatsResponse yearlyAnalytics (@Param((("ownerId"))), @Param(("year")), @Param("now") Instant now)
}
