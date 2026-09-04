package com.sublite.security.infrastructure;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Issuer and resource server are the same process here (a monolith with an
 * admin API, not a multi-service system with a separate identity provider),
 * so one symmetric HS256 key does both signing and verification -
 * NimbusJwtEncoder and NimbusJwtDecoder just wrap the same secret two ways.
 *
 * In a real deployment split across services this would be RS256 (or an
 * external IdP like Keycloak issuing the tokens): the signer holds a
 * private key, every resource server only needs the public key (or a JWKS
 * URL) to verify. Not needed here - swapping to that later only touches
 * this class, callers go through JwtEncoder/JwtDecoder either way.
 */
@Configuration
public class JwtConfig {

    private final SecretKeySpec key;

    JwtConfig(JwtProperties properties) {
        byte[] rawKey = Base64.getDecoder().decode(properties.secret());
        this.key = new SecretKeySpec(rawKey, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
