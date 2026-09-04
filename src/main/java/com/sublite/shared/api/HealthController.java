package com.sublite.shared.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Predates Actuator (day 1-2), kept alongside it rather than replaced:
 * a bare "process is up" check with zero dependencies (no DB/Redis call),
 * which is exactly what a liveness probe should be - don't fail liveness
 * because a downstream dependency is having a bad day, or an orchestrator
 * will kill and restart a perfectly fine process for no reason. Actuator's
 * /actuator/health/readiness (see SecurityConfig, application.yml) is the
 * deeper check that DOES include DB/Redis; /actuator/health/liveness
 * covers the same ground as this endpoint through Boot's own mechanism.
 */
@RestController
public class HealthController {

    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "sublite-core",
                "timestamp", Instant.now(clock).toString()
        );
    }
}
