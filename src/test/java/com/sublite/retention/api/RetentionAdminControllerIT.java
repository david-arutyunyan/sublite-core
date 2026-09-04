package com.sublite.retention.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import com.sublite.retention.api.dto.CreateRetentionOfferRequest;
import com.sublite.retention.api.dto.CreateRetentionStepRequest;
import com.sublite.retention.application.RetentionFlowConfig;
import com.sublite.retention.application.RetentionFlowConfigService;
import com.sublite.retention.domain.RetentionOfferType;
import com.sublite.retention.domain.RetentionStepType;
import com.sublite.shared.api.dto.SetActiveRequest;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * addFilters = false: see PlanAdminControllerIT - JWT auth is
 * AuthControllerIT's job, this is about the retention-admin domain logic.
 * Real Postgres AND real Redis (RetentionFlowConfigService's cache is part
 * of what creatingAStepEvictsTheFlowCache proves).
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
class RetentionAdminControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    @ServiceConnection
    static final RedisContainer redis = new RedisContainer("redis:7");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RetentionFlowConfigService flowConfigService;

    @Test
    void createsAnOfferAndListsIt() throws Exception {
        String code = "offer-" + UUID.randomUUID();

        mockMvc.perform(createOfferRequest(code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/admin/retention/offers"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsADuplicateOfferCode() throws Exception {
        String code = "offer-" + UUID.randomUUID();
        mockMvc.perform(createOfferRequest(code)).andExpect(status().isCreated());

        mockMvc.perform(createOfferRequest(code)).andExpect(status().isConflict());
    }

    @Test
    void createsASurveyStepWithoutAnOffer() throws Exception {
        mockMvc.perform(createStepRequest(randomStepOrder(), RetentionStepType.SURVEY, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SURVEY"))
                .andExpect(jsonPath("$.offerId").doesNotExist());
    }

    @Test
    void offerStepWithoutAnOfferIdIsRejected() throws Exception {
        mockMvc.perform(createStepRequest(randomStepOrder(), RetentionStepType.OFFER, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void offerStepWithAnUnknownOfferIdReturns404() throws Exception {
        mockMvc.perform(createStepRequest(randomStepOrder(), RetentionStepType.OFFER, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsADuplicateStepOrder() throws Exception {
        int stepOrder = randomStepOrder();
        mockMvc.perform(createStepRequest(stepOrder, RetentionStepType.SURVEY, null))
                .andExpect(status().isCreated());

        mockMvc.perform(createStepRequest(stepOrder, RetentionStepType.CONFIRMATION, null))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivatingAndReactivatingAnUnusedOfferTogglesActive() throws Exception {
        UUID offerId = createOffer();

        mockMvc.perform(patch("/admin/retention/offers/{id}/active", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetActiveRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/admin/retention/offers/{id}/active", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetActiveRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * The gap this fixes: RetentionFlowConfigService.loadAndCache() never
     * filtered on offer.isActive(), only step.isActive() - a deactivated
     * offer still reachable through an active OFFER step would silently
     * keep being served to customers. Rather than teach the read side to
     * handle that, the write side now refuses to create it.
     */
    @Test
    void deactivatingAnOfferStillReferencedByAnActiveStepIsRejected() throws Exception {
        UUID offerId = createOffer();
        createStep(RetentionStepType.OFFER, offerId);

        mockMvc.perform(patch("/admin/retention/offers/{id}/active", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetActiveRequest(false))))
                .andExpect(status().isConflict());
    }

    @Test
    void creatingAnOfferStepWithADeactivatedOfferIsRejected() throws Exception {
        UUID offerId = createOffer();
        mockMvc.perform(patch("/admin/retention/offers/{id}/active", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetActiveRequest(false))))
                .andExpect(status().isOk());

        mockMvc.perform(createStepRequest(randomStepOrder(), RetentionStepType.OFFER, offerId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivatingAStepTogglesActive() throws Exception {
        UUID stepId = createStep(RetentionStepType.SURVEY, null);

        mockMvc.perform(patch("/admin/retention/steps/{id}/active", stepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetActiveRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    /**
     * The point of this test: getActiveFlow() is cached (RetentionFlowCache,
     * 5-minute TTL), so if RetentionAdminService.createStep() forgot to call
     * evictCache(), the second getActiveFlow() below would still return the
     * pre-creation snapshot instead of picking up the new step.
     */
    @Test
    void creatingAStepEvictsTheFlowCache() throws Exception {
        RetentionFlowConfig before = flowConfigService.getActiveFlow();
        int beforeCount = before.steps().size();

        UUID stepId = createStep(RetentionStepType.SURVEY, null);

        RetentionFlowConfig after = flowConfigService.getActiveFlow();
        assertThat(after.steps()).hasSize(beforeCount + 1);
        assertThat(after.steps().stream().map(RetentionFlowConfig.StepView::stepId)).contains(stepId);
    }

    private UUID createOffer() throws Exception {
        String body = mockMvc.perform(createOfferRequest("offer-" + UUID.randomUUID()))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createStep(RetentionStepType type, UUID offerId) throws Exception {
        String body = mockMvc.perform(createStepRequest(randomStepOrder(), type, offerId))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private int randomStepOrder() {
        return ThreadLocalRandom.current().nextInt(1000, 1_000_000);
    }

    private MockHttpServletRequestBuilder createOfferRequest(String code) throws Exception {
        CreateRetentionOfferRequest request = new CreateRetentionOfferRequest(
                code, RetentionOfferType.DISCOUNT_PERCENT, Map.of("percent", 20, "periods", 3));
        return post("/admin/retention/offers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }

    private MockHttpServletRequestBuilder createStepRequest(int stepOrder, RetentionStepType type, UUID offerId) throws Exception {
        CreateRetentionStepRequest request = new CreateRetentionStepRequest(stepOrder, type, offerId);
        return post("/admin/retention/steps")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }
}
