package com.sublite.retention.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sublite.retention.application.RetentionCacheProperties;
import com.sublite.retention.application.RetentionFlowConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Deliberately manual (StringRedisTemplate + Jackson) instead of
 * Spring's @Cacheable: the cache-aside steps - check cache, miss, read the
 * real source, populate cache - are worth seeing explicitly rather than
 * hidden behind an annotation, especially for a project meant to explain
 * its own decisions. @Cacheable would be the shorter way to write this in
 * a real production codebase once the pattern doesn't need to be visible.
 */
@Component
public class RetentionFlowCache {

    private static final Logger log = LoggerFactory.getLogger(RetentionFlowCache.class);

    private static final String CACHE_KEY = "retention:active-flow";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RetentionCacheProperties properties;

    public RetentionFlowCache(StringRedisTemplate redis, ObjectMapper objectMapper, RetentionCacheProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<RetentionFlowConfig> get() {
        String json = redis.opsForValue().get(CACHE_KEY);
        if (json == null) {
            log.info("Retention flow cache miss: key={}", CACHE_KEY);
            return Optional.empty();
        }
        try {
            log.info("Retention flow cache hit: key={}", CACHE_KEY);
            return Optional.of(objectMapper.readValue(json, RetentionFlowConfig.class));
        } catch (JsonProcessingException corrupted) {
            log.warn("Retention flow cache entry was corrupted, treating as a miss: key={}", CACHE_KEY, corrupted);
            return Optional.empty();
        }
    }

    public void put(RetentionFlowConfig config) {
        try {
            redis.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(config), properties.ttl());
            log.info("Retention flow cache populated: key={}, ttl={}", CACHE_KEY, properties.ttl());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize retention flow config for caching", e);
        }
    }

    public void evict() {
        redis.delete(CACHE_KEY);
        log.info("Retention flow cache evicted: key={}", CACHE_KEY);
    }
}
