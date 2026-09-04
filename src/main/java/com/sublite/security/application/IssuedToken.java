package com.sublite.security.application;

import java.time.Instant;

public record IssuedToken(String accessToken, Instant expiresAt) {
}
