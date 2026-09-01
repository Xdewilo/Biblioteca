// by Jeremy Posada
package com.jposada.anaquel.infrastructure.config;

import com.jposada.anaquel.infrastructure.openlibrary.OpenLibraryClient;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String OPEN_LIBRARY_CACHE = "openLibraryLookup";

    @Value("${app.openlibrary.cache.ttl-hours:24}")
    private long ttlHours;

    @Value("${app.openlibrary.cache.max-size:1000}")
    private long maxSize;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(OPEN_LIBRARY_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(ttlHours))
                .maximumSize(maxSize)
                .recordStats());
        // Los fallos de Open Library no se cachean (ver OpenLibraryClient).
        manager.setAllowNullValues(false);
        return manager;
    }
}
