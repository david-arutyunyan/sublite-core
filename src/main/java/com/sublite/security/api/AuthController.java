package com.sublite.security.api;

import com.sublite.security.application.AuthService;
import com.sublite.security.application.IssuedToken;
import com.sublite.security.api.dto.LoginRequest;
import com.sublite.security.api.dto.LoginResponse;
import com.sublite.security.api.dto.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Auth", description = "Login and token introspection")
public class AuthController {

    private final AuthService authService;
    private final Clock clock;

    public AuthController(AuthService authService, Clock clock) {
        this.authService = authService;
        this.clock = clock;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and get a JWT",
            description = "Public - only the seeded admin (admin@sublite.dev) has a password to log in with; "
                    + "see User.java for why customers don't.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        IssuedToken token = authService.login(request.email(), request.password());
        long expiresInSeconds = Duration.between(Instant.now(clock), token.expiresAt()).toSeconds();
        return new LoginResponse(token.accessToken(), "Bearer", expiresInSeconds);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Show what the current token's claims are")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("role"));
    }
}
