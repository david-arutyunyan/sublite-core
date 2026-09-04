package com.sublite.retention.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import com.sublite.billing.api.dto.PurchaseSubscriptionRequest;
import com.sublite.billing.domain.ChargeResult;
import com.sublite.billing.domain.PaymentGateway;
import com.sublite.retention.api.dto.SubmitReasonRequest;
import com.sublite.retention.application.RetentionFlowConfigService;
import com.sublite.retention.domain.RetentionOffer;
import com.sublite.retention.domain.RetentionOfferType;
import com.sublite.retention.domain.RetentionStep;
import com.sublite.retention.domain.RetentionStepType;
import com.sublite.retention.infrastructure.CancellationAttemptRepository;
import com.sublite.retention.infrastructure.RetentionOfferRepository;
import com.sublite.retention.infrastructure.RetentionStepRepository;
import com.sublite.security.api.dto.LoginRequest;
import com.sublite.security.api.dto.RegisterRequest;
import com.sublite.subscription.api.dto.CreatePlanRequest;
import com.sublite.subscription.domain.BillingPeriod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fixed step orders 1/2/3 (not randomized like the admin retention tests):
 * getActiveFlow() reads ALL active steps globally, not scoped per test, so
 * each test's steps have to be torn down afterward (see @AfterEach) rather
 * than just given unique orders - same reasoning, same fixture shape as
 * RetentionFlowServiceIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RetentionCustomerControllerIT {

    private static final String ADMIN_EMAIL = "admin@sublite.dev";
    private static final String ADMIN_PASSWORD = "admin123!";

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
    private RetentionStepRepository steps;
    @Autowired
    private RetentionOfferRepository offers;
    @Autowired
    private CancellationAttemptRepository attempts;
    @Autowired
    private RetentionFlowConfigService flowConfigService;

    @MockitoBean
    private PaymentGateway paymentGateway;

    private RetentionOffer pauseOffer;

    @BeforeEach
    void setUp() {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Success("ref-1"));

        Instant now = Instant.now();
        pauseOffer = offers.save(new RetentionOffer(
                UUID.randomUUID(), "PAUSE-" + UUID.randomUUID(), RetentionOfferType.PAUSE_SUBSCRIPTION, Map.of(), now));
        steps.save(new RetentionStep(UUID.randomUUID(), 1, RetentionStepType.SURVEY, null, now));
        steps.save(new RetentionStep(UUID.randomUUID(), 2, RetentionStepType.OFFER, pauseOffer, now));
        steps.save(new RetentionStep(UUID.randomUUID(), 3, RetentionStepType.CONFIRMATION, null, now));
        flowConfigService.evictCache();
    }

    @AfterEach
    void tearDown() {
        attempts.deleteAll();
        steps.deleteAll();
        offers.deleteAll();
        flowConfigService.evictCache();
    }

    @Test
    void acceptingTheOfferRetainsTheCustomer() throws Exception {
        String token = registerCustomer();
        UUID subscriptionId = purchaseSubscription(token);

        String startBody = mockMvc.perform(post("/subscriptions/{id}/cancellation", subscriptionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentStep.type").value("SURVEY"))
                .andReturn().getResponse().getContentAsString();
        UUID attemptId = UUID.fromString(objectMapper.readTree(startBody).get("id").asText());

        mockMvc.perform(reasonRequest(token, attemptId, "too expensive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep.type").value("OFFER"))
                .andExpect(jsonPath("$.currentStep.offerType").value("PAUSE_SUBSCRIPTION"));

        mockMvc.perform(post("/cancellation/{id}/accept-offer", attemptId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETAINED"))
                .andExpect(jsonPath("$.acceptedOfferId").value(pauseOffer.getId().toString()))
                .andExpect(jsonPath("$.currentStep").doesNotExist());
    }

    @Test
    void decliningTheOfferLeadsToConfirmationAndThenCancels() throws Exception {
        String token = registerCustomer();
        UUID subscriptionId = purchaseSubscription(token);
        UUID attemptId = startAttempt(token, subscriptionId);
        mockMvc.perform(reasonRequest(token, attemptId, "too expensive")).andExpect(status().isOk());

        mockMvc.perform(post("/cancellation/{id}/decline-offer", attemptId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentStep.type").value("CONFIRMATION"));

        mockMvc.perform(post("/cancellation/{id}/confirm", attemptId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/subscriptions/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmingBeforeReachingTheConfirmationStepIsRejected() throws Exception {
        String token = registerCustomer();
        UUID subscriptionId = purchaseSubscription(token);
        UUID attemptId = startAttempt(token, subscriptionId);

        mockMvc.perform(post("/cancellation/{id}/confirm", attemptId).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startingCancellationForAnotherCustomersSubscriptionIsRejected() throws Exception {
        String ownerToken = registerCustomer();
        UUID subscriptionId = purchaseSubscription(ownerToken);
        String otherToken = registerCustomer();

        mockMvc.perform(post("/subscriptions/{id}/cancellation", subscriptionId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void readingAnotherCustomersAttemptIsRejected() throws Exception {
        String ownerToken = registerCustomer();
        UUID subscriptionId = purchaseSubscription(ownerToken);
        UUID attemptId = startAttempt(ownerToken, subscriptionId);
        String otherToken = registerCustomer();

        mockMvc.perform(get("/cancellation/{id}", attemptId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private UUID startAttempt(String token, UUID subscriptionId) throws Exception {
        String body = mockMvc.perform(post("/subscriptions/{id}/cancellation", subscriptionId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private MockHttpServletRequestBuilder reasonRequest(String token, UUID attemptId, String reason) throws Exception {
        return post("/cancellation/{id}/reason", attemptId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SubmitReasonRequest(reason)));
    }

    private String registerCustomer() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        String body = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, "correct-horse-battery"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private UUID purchaseSubscription(String customerToken) throws Exception {
        String adminBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String adminToken = objectMapper.readTree(adminBody).get("accessToken").asText();

        CreatePlanRequest planRequest = new CreatePlanRequest(
                "plan-" + UUID.randomUUID(), "Plus", "desc", BillingPeriod.MONTHLY, new BigDecimal("9.99"), "USD");
        String planBody = mockMvc.perform(post("/admin/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planRequest)))
                .andReturn().getResponse().getContentAsString();
        UUID planId = UUID.fromString(objectMapper.readTree(planBody).get("id").asText());

        String pricesBody = mockMvc.perform(get("/admin/plans/{id}/prices", planId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        UUID planPriceId = UUID.fromString(objectMapper.readTree(pricesBody).get(0).get("id").asText());

        String purchaseBody = mockMvc.perform(post("/subscriptions")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PurchaseSubscriptionRequest(planPriceId))))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(purchaseBody).get("id").asText());
    }
}
