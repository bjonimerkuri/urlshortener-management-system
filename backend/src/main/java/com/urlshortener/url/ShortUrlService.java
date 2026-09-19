package com.urlshortener.url;

import com.urlshortener.common.PageResponse;
import com.urlshortener.common.PageUtils;
import com.urlshortener.common.exception.BusinessRuleException;
import com.urlshortener.common.exception.DuplicateResourceException;
import com.urlshortener.common.exception.LinkGoneException;
import com.urlshortener.common.exception.ResourceNotFoundException;
import com.urlshortener.config.AppProperties;
import com.urlshortener.security.AuthenticatedUser;
import com.urlshortener.url.dto.CreateShortUrlRequest;
import com.urlshortener.url.dto.ShortUrlResponse;
import com.urlshortener.url.dto.UpdateShortUrlRequest;
import com.urlshortener.url.dto.UrlStatsResponse;
import com.urlshortener.user.User;
import com.urlshortener.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortUrlService {

    private static final Logger log = LoggerFactory.getLogger(ShortUrlService.class);

    
    static final Set<String> SORTABLE_PROPERTIES =
            Set.of("createdAt", "updatedAt", "shortCode", "originalUrl", "clickCount", "expiresAt", "lastAccessedAt", "status");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ShortUrlRepository shortUrlRepository;
    private final UserRepository userRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final OriginalUrlValidator originalUrlValidator;
    private final AppProperties appProperties;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    public ShortUrlService(
            ShortUrlRepository shortUrlRepository,
            UserRepository userRepository,
            ShortCodeGenerator shortCodeGenerator,
            OriginalUrlValidator originalUrlValidator,
            AppProperties appProperties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.shortUrlRepository = shortUrlRepository;
        this.userRepository = userRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.originalUrlValidator = originalUrlValidator;
        this.appProperties = appProperties;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public ShortUrlResponse create(AuthenticatedUser caller, CreateShortUrlRequest request) {
        Instant now = clock.instant();
        User owner = userRepository
                .findById(caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("The account no longer exists."));

        String originalUrl = originalUrlValidator.validate(request.originalUrl());
        Instant expiresAt = resolveExpiryForNewUrl(request.expiresAt(), now);

        boolean hasAlias = request.customAlias() != null && !request.customAlias().isBlank();
        String code = hasAlias ? claimAlias(request.customAlias()) : allocateGeneratedCode();

        ShortUrl created;
        try {
            created = shortUrlRepository.save(new ShortUrl(code, originalUrl, owner, expiresAt, hasAlias));
        } catch (DataIntegrityViolationException ex) {
            
            throw new DuplicateResourceException("That alias has just been taken. Please choose another.");
        }

        log.info("User {} shortened a URL as {}", caller.id(), code);
        return toResponse(created, now);
    }

    @Transactional(readOnly = true)
    public ShortUrlResponse get(AuthenticatedUser caller, UUID id) {
        return toResponse(loadForCaller(caller, id), clock.instant());
    }

    
    @Transactional(readOnly = true)
    public PageResponse<ShortUrlResponse> search(
            AuthenticatedUser caller, UrlSearchCriteria criteria, Pageable pageable) {
        Instant now = clock.instant();
        Specification<ShortUrl> specification =
                baseSpecification(criteria, now).and(ShortUrlSpecifications.ownedBy(caller.id()));
        return page(specification, pageable, now);
    }

    
    @Transactional(readOnly = true)
    public PageResponse<ShortUrlResponse> searchAll(UrlSearchCriteria criteria, Pageable pageable) {
        Instant now = clock.instant();
        Specification<ShortUrl> specification = baseSpecification(criteria, now)
                .and(ShortUrlSpecifications.ownerEmailContains(criteria.ownerEmail()));
        return page(specification, pageable, now);
    }

    @Transactional
    public ShortUrlResponse update(AuthenticatedUser caller, UUID id, UpdateShortUrlRequest request) {
        Instant now = clock.instant();
        ShortUrl url = loadForCaller(caller, id);

        if (request.expiresAt() != null) {
            requireWithinMaxTtl(request.expiresAt(), now);
        }
        
        url.changeExpiresAt(request.expiresAt(), now);
        if (Boolean.TRUE.equals(request.active())) {
            url.activate(now);
        } else {
            url.deactivate();
        }
        return toResponse(shortUrlRepository.save(url), now);
    }

    @Transactional
    public ShortUrlResponse setActive(AuthenticatedUser caller, UUID id, boolean active) {
        Instant now = clock.instant();
        ShortUrl url = loadForCaller(caller, id);
        if (active) {
            if (url.isExpiredAt(now)) {
                throw new BusinessRuleException("This link has expired. Extend its expiration date to reactivate it.");
            }
            url.activate(now);
        } else {
            url.deactivate();
        }
        return toResponse(shortUrlRepository.save(url), now);
    }

    @Transactional
    public void delete(AuthenticatedUser caller, UUID id) {
        shortUrlRepository.delete(loadForCaller(caller, id));
        log.info("User {} deleted short URL {}", caller.id(), id);
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse statsFor(AuthenticatedUser caller) {
        return shortUrlRepository.statsForOwner(caller.id(), clock.instant());
    }
    @Transactional(readOnly = true)
    public UrlStatsResponse analytics(AuthenticatedUser caller){
        return shortUrlRepository.yearlyAnalytics(caller.id(), clock.instant());
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse globalStats() {
        return shortUrlRepository.statsForAllOwners(clock.instant());
    }

    

    @Transactional
    public String resolveForRedirect(String shortCode) {
        Instant now = clock.instant();
        ShortUrl url = shortUrlRepository
                .findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("No such short link."));

        
        UUID id = url.getId();
        String target = url.getOriginalUrl();

        if (url.isExpiredAt(now)) {
            if (url.getStatus() != UrlStatus.EXPIRED) {
                
                
                
                url.markExpired();
                requiresNew.executeWithoutResult(status -> shortUrlRepository.save(url));
            }
            throw new LinkGoneException("This link has expired.");
        }
        if (url.getStatus() != UrlStatus.ACTIVE) {
            throw new LinkGoneException("This link has been deactivated.");
        }

        shortUrlRepository.registerClick(id, now);
        return target;
    }

    private PageResponse<ShortUrlResponse> page(
            Specification<ShortUrl> specification, Pageable pageable, Instant now) {
        Pageable sanitized = PageUtils.sanitize(pageable, SORTABLE_PROPERTIES, DEFAULT_SORT);
        Page<ShortUrl> results = shortUrlRepository.findAll(specification, sanitized);
        return PageResponse.from(results, entity -> toResponse(entity, now));
    }

    private Specification<ShortUrl> baseSpecification(UrlSearchCriteria criteria, Instant now) {
        return Specification.allOf(
                ShortUrlSpecifications.withOwnerFetched(),
                ShortUrlSpecifications.matching(criteria.search()),
                ShortUrlSpecifications.withStatus(criteria.status(), now),
                ShortUrlSpecifications.createdFrom(criteria.createdFrom()),
                ShortUrlSpecifications.createdUntil(criteria.createdTo()));
    }

    

    private ShortUrl loadForCaller(AuthenticatedUser caller, UUID id) {
        return (caller.isAdmin()
                        ? shortUrlRepository.findById(id)
                        : shortUrlRepository.findByIdAndOwnerId(id, caller.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found."));
    }

    private String claimAlias(String requestedAlias) {
        String alias = requestedAlias.trim();
        if (appProperties.shortUrl().reservedAliasSet().contains(alias.toLowerCase(Locale.ROOT))) {
            throw new BusinessRuleException("'" + alias + "' is reserved and cannot be used as an alias.");
        }
        if (shortUrlRepository.existsByShortCode(alias)) {
            throw new DuplicateResourceException("The alias '" + alias + "' is already in use.");
        }
        return alias;
    }

    

    private String allocateGeneratedCode() {
        AppProperties.ShortUrl config = appProperties.shortUrl();
        for (int attempt = 0; attempt < config.maxGenerationAttempts(); attempt++) {
            String candidate = shortCodeGenerator.generate(config.codeLength());
            if (!shortUrlRepository.existsByShortCode(candidate)) {
                return candidate;
            }
            log.warn("Short code collision on attempt {} of {}", attempt + 1, config.maxGenerationAttempts());
        }
        throw new IllegalStateException(
            "Could not create a unique short code after " + config.maxGenerationAttempts()
                + " attempts. Try increasing app.short-url.code-length.");
    }

    private Instant resolveExpiryForNewUrl(Instant requested, Instant now) {
        if (requested == null) {
            return now.plus(appProperties.shortUrl().defaultTtl());
        }
        if (!requested.isAfter(now)) {
            throw new BusinessRuleException("The expiration date must be in the future.");
        }
        requireWithinMaxTtl(requested, now);
        return requested;
    }

    private void requireWithinMaxTtl(Instant requested, Instant now) {
        Instant latest = now.plus(appProperties.shortUrl().maxTtl());
        if (requested.isAfter(latest)) {
            throw new BusinessRuleException(
                    "The expiration date cannot be more than " + appProperties.shortUrl().maxTtl().toDays()
                            + " days in the future.");
        }
    }

    private ShortUrlResponse toResponse(ShortUrl entity, Instant now) {
        return ShortUrlResponse.from(entity, appProperties.shortUrl().toShortUrl(entity.getShortCode()), now);
    }
}
