package com.yourorg.deploy.config;

import java.util.Arrays;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.Eh107Configuration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * deploymentStatus — 30 s TTL, max 100 entries (one per env name).
     * Prevents the dashboard from hammering OpenShift on every 5-second poll.
     */
    private static final Duration DEPLOYMENT_STATUS_TTL = Duration.ofSeconds(30);
    private static final int     DEPLOYMENT_STATUS_ENTRIES = 100;

    /**
     * filterList — 5 min TTL, max 50 entries (one per activeOnly+category combo).
     * Filter definitions rarely change; long TTL keeps dropdowns instant.
     */
    private static final Duration FILTER_LIST_TTL = Duration.ofMinutes(5);
    private static final int      FILTER_LIST_ENTRIES = 50;

    @Bean
    public CacheManager ehCacheManager() {
        CachingProvider provider = Caching.getCachingProvider();
        CacheManager cacheManager = provider.getCacheManager();

        cacheManager.createCache("deploymentStatus",
                Eh107Configuration.fromEhcacheCacheConfiguration(
                        CacheConfigurationBuilder
                                .newCacheConfigurationBuilder(String.class, Object.class,
                                        ResourcePoolsBuilder.heap(DEPLOYMENT_STATUS_ENTRIES))
                                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(DEPLOYMENT_STATUS_TTL))
                                .build()));

        cacheManager.createCache("filterList",
                Eh107Configuration.fromEhcacheCacheConfiguration(
                        CacheConfigurationBuilder
                                .newCacheConfigurationBuilder(String.class, Object.class,
                                        ResourcePoolsBuilder.heap(FILTER_LIST_ENTRIES))
                                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(FILTER_LIST_TTL))
                                .build()));

        return cacheManager;
    }
}
