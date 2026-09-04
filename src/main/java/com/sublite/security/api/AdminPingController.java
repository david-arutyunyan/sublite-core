package com.sublite.security.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Placeholder proving /admin/** -> ROLE_ADMIN actually works end to end,
 * ahead of the real admin endpoints (plans, retention config, loyalty
 * rules) landing in the next step. Delete this once those exist.
 */
@RestController
@RequestMapping("/admin")
public class AdminPingController {

    @GetMapping("/ping")
    public Map<String, String> ping(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("status", "ok", "admin", jwt.getClaimAsString("email"));
    }
}
