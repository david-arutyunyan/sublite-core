package com.sublite.retention.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "sublite.retention.flow-cache")
public record RetentionCacheProperties(Duration ttl) {

    public RetentionCacheProperties {
        if (ttl == null) {
            ttl = Duration.ofMinutes(5);
        }
    }
}
