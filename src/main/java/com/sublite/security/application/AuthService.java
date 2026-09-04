package com.sublite.security.application;

import com.sublite.security.domain.EmailAlreadyRegisteredException;
import com.sublite.security.domain.InvalidCredentialsException;
import com.sublite.security.infrastructure.JwtProperties;
import com.sublite.shared.domain.Role;
import com.sublite.shared.domain.User;
import com.sublite.shared.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /**
     * passwordEncoder.matches() still runs against a fixed dummy hash when
     * no user/no password-having user is found - a constant-time
     * bcrypt.matches() call either way keeps the response time the same
     * whether the email exists or not, closing a timing side-channel.
     */
    @Transactional(readOnly = true)
    public IssuedToken login(String email, String rawPassword) {
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        String hashToCheck = (user != null && user.getPasswordHash() != null)
                ? user.getPasswordHash()
                : DUMMY_HASH;

        boolean matches = passwordEncoder.matches(rawPassword, hashToCheck);
        if (user == null || user.getPasswordHash() == null || !matches) {
            log.warn("Login failed: email={}", email);
            throw new InvalidCredentialsException();
        }

        log.info("Login succeeded: userId={}, email={}", user.getId(), email);
        return issueToken(user);
    }

    /**
     * Registers a CUSTOMER account and logs them straight in - one fewer
     * round trip than register-then-separately-call-/auth/login, and
     * there's no email verification step in this project to make "you're
     * registered but not logged in yet" a meaningful intermediate state.
     */
    @Transactional
    public IssuedToken register(String email, String rawPassword) {
        users.findByEmailIgnoreCase(email).ifPresent(existing -> {
            throw new EmailAlreadyRegisteredException(email);
        });

        Instant now = Instant.now(clock);
        User user = users.save(new User(
                UUID.randomUUID(), email, Role.CUSTOMER, passwordEncoder.encode(rawPassword), now, now
        ));
        log.info("Registered new customer: userId={}, email={}", user.getId(), email);
        return issueToken(user);
    }

    private IssuedToken issueToken(User user) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(jwtProperties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();

        // NimbusJwtEncoder defaults to RS256 when no header is given, which
        // can't be satisfied by our symmetric key - HS256 has to be
        // requested explicitly to match the JwtConfig JWKSource.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    private static final String DUMMY_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5L4pdlWMD08cQfKmuUnFa5.CkdOhu";
}
