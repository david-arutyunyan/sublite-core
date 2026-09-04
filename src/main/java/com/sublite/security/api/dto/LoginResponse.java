package com.sublite.security.api.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
