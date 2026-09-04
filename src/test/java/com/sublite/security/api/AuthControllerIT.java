package com.sublite.security.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sublite.security.api.dto.LoginRequest;
import com.sublite.security.infrastructure.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SecurityConfig/JwtConfig (unlike the @WebMvcTest
 * slices elsewhere, which disable the filter chain) - login against the
 * admin user seeded by V23, then use the token against a role-gated
 * endpoint. The CUSTOMER-role case mints its own JWT directly through the
 * same JwtEncoder bean AuthService uses, since customers have no password
 * to log in with in this project (see User.java) - this is still a
 * genuine token the app itself would issue for a customer, just without
 * going through the /auth/login round trip.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIT {

    private static final String ADMIN_EMAIL = "admin@sublite.dev";
    private static final String ADMIN_PASSWORD = "admin123!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private Clock clock;

    @Test
    void loginWithSeededAdminReturnsToken() throws Exception {
        mockMvc.perform(loginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(loginRequest(ADMIN_EMAIL, "not-the-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownEmailIsRejected() throws Exception {
        mockMvc.perform(loginRequest("nobody@sublite.dev", "whatever"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointRequiresAToken() throws Exception {
        mockMvc.perform(get("/admin/plans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointRejectsACustomerToken() throws Exception {
        String token = issueToken("customer@example.com", "CUSTOMER");

        mockMvc.perform(get("/admin/plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAcceptsAnAdminToken() throws Exception {
        String accessToken = login();

        mockMvc.perform(get("/admin/plans").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(String email, String password) throws Exception {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password)));
    }

    private String login() throws Exception {
        String body = mockMvc.perform(loginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String issueToken(String email, String role) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("email", email)
                .claim("role", role)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
