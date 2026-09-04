package com.sublite.subscription.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sublite.subscription.api.dto.AddPlanPriceRequest;
import com.sublite.subscription.api.dto.CreatePlanRequest;
import com.sublite.subscription.api.dto.SetPlanActiveRequest;
import com.sublite.subscription.domain.BillingPeriod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * addFilters = false: JWT auth itself is AuthControllerIT's job (login,
 * 401/403/200 on a role-gated endpoint) - re-proving that here on every
 * admin controller would just be noise. This test is about the plan-admin
 * domain logic: creation, price versioning, activation, not-found/conflict
 * responses.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
class PlanAdminControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAPlanWithItsFirstPrice() throws Exception {
        String code = "plan-" + UUID.randomUUID();

        mockMvc.perform(createPlanRequest(code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void rejectsADuplicatePlanCode() throws Exception {
        String code = "plan-" + UUID.randomUUID();
        mockMvc.perform(createPlanRequest(code)).andExpect(status().isCreated());

        mockMvc.perform(createPlanRequest(code)).andExpect(status().isConflict());
    }

    @Test
    void returns404ForAnUnknownPlan() throws Exception {
        mockMvc.perform(get("/admin/plans/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivateAndActivateTogglePlanState() throws Exception {
        UUID planId = createPlan();

        mockMvc.perform(patch("/admin/plans/{id}/active", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetPlanActiveRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/admin/plans/{id}", planId))
                .andExpect(jsonPath("$.active").value(false));
    }

    /**
     * The real proof this works is that the second insert doesn't blow up:
     * plan_prices has an EXCLUDE constraint rejecting two overlapping
     * valid_periods for the same plan + billing period (V4 migration) - if
     * PlanAdminService.setPrice() failed to close the first price before
     * inserting the second, this 201 would be a 500 instead. The
     * JdbcTemplate check below additionally confirms exactly one row is
     * left open-ended, since valid_period isn't mapped into PlanPrice/Java
     * at all (see PlanPrice.java) so the MockMvc response can't show it.
     */
    @Test
    void versioningAPriceClosesTheOldOneAndOpensANewOne() throws Exception {
        UUID planId = createPlan();

        mockMvc.perform(post("/admin/plans/{id}/prices", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddPlanPriceRequest(BillingPeriod.MONTHLY, new BigDecimal("14.99"), "USD"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(14.99));

        String history = mockMvc.perform(get("/admin/plans/{id}/prices", planId))
                .andReturn().getResponse().getContentAsString();
        JsonNode prices = objectMapper.readTree(history);
        assertThat(prices).hasSize(2);

        Integer openEndedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM subscription.plan_prices WHERE plan_id = ? AND billing_period = 'MONTHLY' AND upper_inf(valid_period)",
                Integer.class, planId);
        assertThat(openEndedCount).isEqualTo(1);
    }

    private UUID createPlan() throws Exception {
        String body = mockMvc.perform(createPlanRequest("plan-" + UUID.randomUUID()))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private MockHttpServletRequestBuilder createPlanRequest(String code) throws Exception {
        CreatePlanRequest request = new CreatePlanRequest(
                code, "Plus", "Sublite Plus plan", BillingPeriod.MONTHLY, new BigDecimal("9.99"), "USD");
        return post("/admin/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }
}
