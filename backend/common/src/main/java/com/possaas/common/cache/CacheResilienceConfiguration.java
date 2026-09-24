package com.possaas.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps a sick cache from becoming a sick shop.
 *
 * <p>The cache backend is Redis, and on the free hosting tier that instance has no
 * persistence and no availability promise. By default Spring lets a Redis failure
 * propagate out of the cached method, and {@code TenantService.findBySlug} is both
 * cached and on the sign-in path - so a Redis blip would stop every till in every
 * shop from logging in, to protect a lookup the database can answer in a
 * millisecond.
 *
 * <p>A cache is an optimisation, so every cache failure is logged and swallowed: a
 * failed read behaves as a miss and falls through to the database, and a failed
 * write or eviction leaves the value to expire on its own TTL. The one thing this
 * deliberately does not hide is a failure to evict, which is logged at WARN because
 * it can leave a stale tenant or setting readable until the TTL lapses.
 */
@Configuration
public class CacheResilienceConfiguration implements CachingConfigurer {

    private static final Logger log =
            LoggerFactory.getLogger(CacheResilienceConfiguration.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache read failed for {}[{}]; falling through to the source",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache,
                                            Object key, Object value) {
                log.warn("Cache write failed for {}[{}]; the value was still returned",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache evict failed for {}[{}]; a stale entry may be served "
                        + "until its TTL expires", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache clear failed for {}; stale entries may be served "
                        + "until their TTL expires", cache.getName(), exception);
            }
        };
    }
}
