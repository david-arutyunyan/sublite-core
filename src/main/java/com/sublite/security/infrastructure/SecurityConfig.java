package com.sublite.security.infrastructure;

import com.sublite.shared.infrastructure.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Collection;
import java.util.List;

/**
 * Authorization is path-prefix based (/admin/** -> ROLE_ADMIN) rather than
 * @PreAuthorize scattered across controller methods: the admin API is a
 * genuinely separate set of endpoints by URL (per the spec - "отдельная
 * роль, отдельный набор эндпоинтов"), so the boundary belongs here, in one
 * place that's easy to audit, not spread across every controller.
 *
 * CSRF is disabled: this API is stateless bearer-token auth with no
 * browser cookie/session in the mix, which is exactly the case CSRF
 * protection doesn't apply to (it defends session cookies that browsers
 * attach automatically; a JWT in an Authorization header isn't attached
 * automatically by anything).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Before UsernamePasswordAuthenticationFilter - the usual
                // anchor point for "run early, before authentication" -
                // so every log line for a request, including auth
                // failures, carries a correlation id. Added here rather
                // than as a @Component: see CorrelationIdFilter's javadoc
                // for why that would run it twice.
                .addFilterBefore(new CorrelationIdFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/health",
                                "/auth/login",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleClaimConverter())));

        return http.build();
    }

    /**
     * The default JwtGrantedAuthoritiesConverter reads OAuth2 "scope"/"scp"
     * claims and prefixes each with SCOPE_. We issue one "role" claim
     * (see AuthService) and need it as ROLE_<value>, not SCOPE_<value>, so
     * Spring's hasRole("ADMIN") check works - hence the converter here
     * instead of the default.
     */
    private Converter<Jwt, ? extends AbstractAuthenticationToken> roleClaimConverter() {
        var delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(SecurityConfig::authoritiesFromRoleClaim);
        return delegate;
    }

    private static Collection<GrantedAuthority> authoritiesFromRoleClaim(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
