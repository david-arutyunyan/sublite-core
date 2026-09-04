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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    /**
     * Two admins (or a double-submitted form) setting the rule for the
     * same event type at once: deactivateActive() + insert isn't atomic
     * across both calls, so both inserts can race for the same
     * uq_loyalty_rules_active_event_type slot. LoyaltyRuleAdminService.
     * setRule() retries the loser against a fresh transaction instead of
     * letting DataIntegrityViolationException reach the controller - both
     * calls should end up 201, and exactly one rule should be left active
     * afterward, never zero and never two.
     */
    @Test
    void concurrentSetRuleCallsBothSucceedAndExactlyOneRuleEndsUpActive() throws Exception {
        CyclicBarrier bothReady = new CyclicBarrier(2);
        Callable<Integer> setTo50 = () -> setRuleAndGetStatus(50, bothReady);
        Callable<Integer> setTo75 = () -> setRuleAndGetStatus(75, bothReady);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = pool.invokeAll(List.of(setTo50, setTo75));
        pool.shutdown();

        for (Future<Integer> result : results) {
            assertThat(result.get()).as("both concurrent sets should succeed, not surface the race as an error").isEqualTo(201);
        }

        String body = mockMvc.perform(get("/admin/loyalty/rules")).andReturn().getResponse().getContentAsString();
        JsonNode rules = objectMapper.readTree(body);
        long activeCount = StreamSupport.stream(rules.spliterator(), false)
                .filter(rule -> rule.get("active").asBoolean())
                .count();
        assertThat(activeCount).isEqualTo(1);
    }

    private int setRuleAndGetStatus(int points, CyclicBarrier bothReady) throws Exception {
        bothReady.await();
        return mockMvc.perform(setRuleRequest(points)).andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder setRuleRequest(int points) throws Exception {
        SetLoyaltyRuleRequest request = new SetLoyaltyRuleRequest(LoyaltyEventType.PAYMENT_SUCCESS, points);
        return post("/admin/loyalty/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }
}
