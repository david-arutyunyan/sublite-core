package com.sublite.loyalty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sublite.billing.api.dto.PurchaseSubscriptionRequest;
import com.sublite.billing.domain.ChargeResult;
import com.sublite.billing.domain.PaymentGateway;
import com.sublite.loyalty.api.dto.SetLoyaltyRuleRequest;
import com.sublite.loyalty.domain.LoyaltyEventType;
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
 * Real SecurityConfig/JwtConfig (@AutoConfigureMockMvc). No Redis
 * container - unlike retention, loyalty rules aren't cached (see
 * LoyaltyRuleAdminService's javadoc), so nothing here needs it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LoyaltyControllerIT {

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
    void balanceIsZeroForACustomerWhoHasNeverEarnedAnyPoints() throws Exception {
        String token = registerCustomer();

        mockMvc.perform(get("/loyalty/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void balanceRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/loyalty/me")).andExpect(status().isUnauthorized());
    }

    /**
     * Ties A2 and A3 together end to end: an admin-configured PAYMENT_SUCCESS
     * rule + a real purchase (mocked gateway success) should show up in the
     * customer's own balance, with no code specific to this test path -
     * BillingOrchestrator.processOne() already calls
     * loyaltyService.awardForEvent() on a successful charge (see its
     * javadoc), purchasing is just the first charge a subscription ever gets.
     */
    @Test
    void aSuccessfulPurchaseAwardsThePointsConfiguredForPaymentSuccess() throws Exception {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Success("ref-1"));
        String adminToken = adminToken();
        mockMvc.perform(post("/admin/loyalty/rules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetLoyaltyRuleRequest(LoyaltyEventType.PAYMENT_SUCCESS, 50))))
                .andExpect(status().isCreated());

        String customerToken = registerCustomer();
        purchaseSubscription(customerToken, adminToken);

        mockMvc.perform(get("/loyalty/me").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50));
    }

    @Test
    void historyIsEmptyForACustomerWhoHasNeverEarnedAnyPoints() throws Exception {
        String token = registerCustomer();

        mockMvc.perform(get("/loyalty/me/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void historyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/loyalty/me/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSuccessfulPurchaseShowsUpAsAnEarnTransactionInHistory() throws Exception {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Success("ref-1"));
        String adminToken = adminToken();
        mockMvc.perform(post("/admin/loyalty/rules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetLoyaltyRuleRequest(LoyaltyEventType.PAYMENT_SUCCESS, 50))))
                .andExpect(status().isCreated());

        String customerToken = registerCustomer();
        purchaseSubscription(customerToken, adminToken);

        mockMvc.perform(get("/loyalty/me/transactions").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("EARN"))
                .andExpect(jsonPath("$[0].points").value(50))
                .andExpect(jsonPath("$[0].reason").value("PAYMENT_SUCCESS"));
    }

    private String registerCustomer() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        String body = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, "correct-horse-battery"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private void purchaseSubscription(String customerToken, String adminToken) throws Exception {
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

        mockMvc.perform(post("/subscriptions")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PurchaseSubscriptionRequest(planPriceId))))
                .andExpect(status().isCreated());
    }
}
