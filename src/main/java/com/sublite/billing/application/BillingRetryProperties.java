package com.sublite.billing.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "sublite.billing.retry")
public record BillingRetryProperties(Duration baseInterval, int maxAttempts) {

    public BillingRetryProperties {
        if (baseInterval == null) {
            baseInterval = Duration.ofDays(1);
        }
        if (maxAttempts <= 0) {
            maxAttempts = 3;
        }
    }
}
