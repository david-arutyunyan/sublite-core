package com.sublite.security.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * secret is a base64-encoded HMAC key (32+ bytes - HS256 requires at least
 * 256 bits, NimbusJwtEncoder/Decoder reject anything shorter). The
 * application.yml default is fine for local dev and CI; a real deployment
 * overrides it via SUBLITE_JWT_SECRET so the signing key never lives in
 * version control.
 */
@ConfigurationProperties(prefix = "sublite.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {

    public JwtProperties {
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofHours(1);
        }
    }
}
