package com.sublite.security.api;

import com.sublite.security.application.AuthService;
import com.sublite.security.application.IssuedToken;
import com.sublite.security.api.dto.LoginRequest;
import com.sublite.security.api.dto.LoginResponse;
import com.sublite.security.api.dto.MeResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final Clock clock;

    public AuthController(AuthService authService, Clock clock) {
        this.authService = authService;
        this.clock = clock;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        IssuedToken token = authService.login(request.email(), request.password());
        long expiresInSeconds = Duration.between(Instant.now(clock), token.expiresAt()).toSeconds();
        return new LoginResponse(token.accessToken(), "Bearer", expiresInSeconds);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("role"));
    }
}
