package com.sublite.billing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sublite.billing.api.dto.PurchaseSubscriptionRequest;
import com.sublite.billing.domain.ChargeResult;
import com.sublite.billing.domain.PaymentGateway;
import com.sublite.security.api.dto.LoginRequest;
import com.sublite.security.api.dto.RegisterRequest;
import com.sublite.subscription.api.dto.CreatePlanRequest;
import com.sublite.subscription.domain.BillingPeriod;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real SecurityConfig/JwtConfig (@AutoConfigureMockMvc) - purchasing goes
 * through /auth/register for a real CUSTOMER token, not a hand-minted one,
 * since the point is exercising the actual customer signup-to-purchase
 * path end to end. PaymentGateway is mocked, same reasoning as
 * BillingOrchestratorIT: the test controls the charge outcome instead of
 * depending on RandomPaymentGateway's dice roll.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SubscriptionControllerIT {

    private static final String ADMIN_EMAIL = "admin@sublite.dev";
    private static final String ADMIN_PASSWORD = "admin123!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Test
    void purchaseWithASuccessfulChargeReturnsAnActiveSubscription() throws Exception {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Success("ref-1"));
        UUID planPriceId = createPlanAsAdmin();
        String customerToken = registerCustomer();

        mockMvc.perform(purchaseRequest(customerToken, planPriceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.billingPeriod").value("MONTHLY"))
                .andExpect(jsonPath("$.amount").value(9.99));
    }

    @Test
    void purchaseWithADeclinedChargeStillCreatesTheSubscriptionInGracePeriod() throws Exception {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Declined("INSUFFICIENT_FUNDS"));
        UUID planPriceId = createPlanAsAdmin();
        String customerToken = registerCustomer();

        mockMvc.perform(purchaseRequest(customerToken, planPriceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("GRACE_PERIOD"));
    }

    @Test
    void purchasingASecondTimeIsRejected() throws Exception {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Success("ref-1"));
        UUID planPriceId = createPlanAsAdmin();
        String customerToken = registerCustomer();
        mockMvc.perform(purchaseRequest(customerToken, planPriceId)).andExpect(status().isCreated());

        mockMvc.perform(purchaseRequest(customerToken, planPriceId)).andExpect(status().isConflict());
    }

    @Test
    void purchaseWithAnUnknownPlanPriceIdReturns404() throws Exception {
        String customerToken = registerCustomer();

        mockMvc.perform(purchaseRequest(customerToken, UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void purchaseWithoutATokenIsRejected() throws Exception {
        UUID planPriceId = createPlanAsAdmin();

        mockMvc.perform(post("/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PurchaseSubscriptionRequest(planPriceId))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMineReturns404WhenNeverSubscribed() throws Exception {
        String customerToken = registerCustomer();

        mockMvc.perform(get("/subscriptions/me").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMineReturnsTheJustPurchasedSubscription() throws Exception {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Success("ref-1"));
        String planCode = "plan-" + UUID.randomUUID();
        UUID planPriceId = createPlanAsAdmin(planCode);
        String customerToken = registerCustomer();
        mockMvc.perform(purchaseRequest(customerToken, planPriceId)).andExpect(status().isCreated());

        mockMvc.perform(get("/subscriptions/me").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.planCode").value(planCode));
    }

    private MockHttpServletRequestBuilder purchaseRequest(String token, UUID planPriceId) throws Exception {
        return post("/subscriptions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PurchaseSubscriptionRequest(planPriceId)));
    }

    private String registerCustomer() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        String body = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, "correct-horse-battery"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private UUID createPlanAsAdmin() throws Exception {
        return createPlanAsAdmin("plan-" + UUID.randomUUID());
    }

    private UUID createPlanAsAdmin(String code) throws Exception {
        String adminBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String adminToken = objectMapper.readTree(adminBody).get("accessToken").asText();

        CreatePlanRequest request = new CreatePlanRequest(
                code, "Plus", "desc", BillingPeriod.MONTHLY, new BigDecimal("9.99"), "USD");
        String planBody = mockMvc.perform(post("/admin/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        UUID planId = UUID.fromString(objectMapper.readTree(planBody).get("id").asText());

        String pricesBody = mockMvc.perform(get("/admin/plans/{id}/prices", planId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(pricesBody).get(0).get("id").asText());
    }
}
