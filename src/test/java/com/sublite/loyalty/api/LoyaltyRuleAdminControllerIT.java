package com.sublite.loyalty.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sublite.loyalty.api.dto.SetLoyaltyRuleRequest;
import com.sublite.loyalty.domain.LoyaltyEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * addFilters = false: see PlanAdminControllerIT.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
class LoyaltyRuleAdminControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void setRuleCreatesAnActiveRule() throws Exception {
        mockMvc.perform(setRuleRequest(50))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("PAYMENT_SUCCESS"))
                .andExpect(jsonPath("$.points").value(50))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void rejectsNonPositivePoints() throws Exception {
        mockMvc.perform(setRuleRequest(0)).andExpect(status().isBadRequest());
    }

    /**
     * loyalty_rules only allows one active row per event type (V21's
     * partial unique index) - re-setting the rule must deactivate the old
     * row rather than error out, and the old row should still be there as
     * history, just inactive.
     */
    @Test
    void reSettingARuleDeactivatesTheOldOneAndActivatesTheNew() throws Exception {
        mockMvc.perform(setRuleRequest(50)).andExpect(status().isCreated());
        mockMvc.perform(setRuleRequest(75)).andExpect(status().isCreated());

        String body = mockMvc.perform(get("/admin/loyalty/rules"))
                .andReturn().getResponse().getContentAsString();
        JsonNode rules = objectMapper.readTree(body);

        long activeCount = StreamSupport.stream(rules.spliterator(), false)
                .filter(rule -> rule.get("active").asBoolean())
                .count();
        assertThat(activeCount).isEqualTo(1);

        JsonNode active = StreamSupport.stream(rules.spliterator(), false)
                .filter(rule -> rule.get("active").asBoolean())
                .findFirst().orElseThrow();
        assertThat(active.get("points").asInt()).isEqualTo(75);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder setRuleRequest(int points) throws Exception {
        SetLoyaltyRuleRequest request = new SetLoyaltyRuleRequest(LoyaltyEventType.PAYMENT_SUCCESS, points);
        return post("/admin/loyalty/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }
}
