package com.sublite.subscription.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sublite.shared.api.dto.SetActiveRequest;
import com.sublite.subscription.api.dto.CreatePlanRequest;
import com.sublite.subscription.domain.BillingPeriod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real SecurityConfig/JwtConfig (@AutoConfigureMockMvc, not addFilters =
 * false as in PlanAdminControllerIT) - the whole point of these tests is
 * proving GET /plans works with NO Authorization header under the real
 * filter chain, not just that a slice with security switched off happens
 * to allow it through.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlanControllerIT {

    private static final String ADMIN_EMAIL = "admin@sublite.dev";
    private static final String ADMIN_PASSWORD = "admin123!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listIsPublicAndShowsActivePlansWithCurrentPrice() throws Exception {
        String code = "plan-" + UUID.randomUUID();
        createPlanAsAdmin(code);

        String body = mockMvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode plans = objectMapper.readTree(body);
        JsonNode created = findByCode(plans, code);
        assertThat(created).isNotNull();
        assertThat(created.get("prices")).hasSize(1);
        assertThat(created.get("prices").get(0).get("amount").asDouble()).isEqualTo(9.99);
        assertThat(created.get("prices").get(0).get("currency").asText()).isEqualTo("USD");
    }

    @Test
    void deactivatedPlansAreNotListed() throws Exception {
        String code = "plan-" + UUID.randomUUID();
        UUID planId = createPlanAsAdmin(code);

        String adminToken = adminToken();
        mockMvc.perform(patch("/admin/plans/{id}/active", planId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetActiveRequest(false))))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/plans"))
                .andReturn().getResponse().getContentAsString();
        assertThat(findByCode(objectMapper.readTree(body), code)).isNull();
    }

    private JsonNode findByCode(JsonNode plans, String code) {
        for (JsonNode plan : plans) {
            if (plan.get("code").asText().equals(code)) {
                return plan;
            }
        }
        return null;
    }

    private UUID createPlanAsAdmin(String code) throws Exception {
        String adminToken = adminToken();
        CreatePlanRequest request = new CreatePlanRequest(
                code, "Plus", "desc", BillingPeriod.MONTHLY, new BigDecimal("9.99"), "USD");

        String body = mockMvc.perform(post("/admin/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String adminToken() throws Exception {
        MockHttpServletRequestBuilder loginRequest = post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}");
        String body = mockMvc.perform(loginRequest).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
