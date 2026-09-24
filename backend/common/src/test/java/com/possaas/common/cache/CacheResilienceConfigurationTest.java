package com.possaas.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheErrorHandler;

class CacheResilienceConfigurationTest {

    private final CacheErrorHandler handler = new CacheResilienceConfiguration().errorHandler();
    private final Cache cache = new ConcurrentMapCache("tenantBySlug");

    /**
     * The failure that matters: Redis is unreachable while a till signs in. The read
     * must degrade to a miss rather than propagating, or nobody can log in.
     */
    @Test
    void aReadFailureIsSwallowedSoTheCallerFallsThroughToTheDatabase() {
        RuntimeException down = redisIsDown();

        assertThatCode(() -> handler.handleCacheGetError(down, cache, "acme-phones"))
                .doesNotThrowAnyException();
    }

    @Test
    void writeEvictAndClearFailuresAreSwallowedToo() {
        RuntimeException down = redisIsDown();

        assertThatCode(() -> {
            handler.handleCachePutError(down, cache, "acme-phones", "tenant");
            handler.handleCacheEvictError(down, cache, "acme-phones");
            handler.handleCacheClearError(down, cache);
        }).doesNotThrowAnyException();
    }

    /**
     * Stands in for RedisConnectionFailureException, which this module does not
     * depend on. The handler contract is RuntimeException, so the distinction does
     * not reach the code under test.
     */
    private static RuntimeException redisIsDown() {
        return new IllegalStateException("Unable to connect to Redis");
    }

    @Test
    void theHandlerIsWiredInAsTheCachingConfigurerContract() {
        assertThat(new CacheResilienceConfiguration().errorHandler()).isNotNull();
    }
}
