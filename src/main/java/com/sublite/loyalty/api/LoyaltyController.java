package com.sublite.loyalty.api;

import com.sublite.loyalty.api.dto.LoyaltyBalanceResponse;
import com.sublite.loyalty.api.dto.LoyaltyTransactionResponse;
import com.sublite.loyalty.application.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Authenticated (any customer). customerId from the JWT subject, same
 * IDOR reasoning as everywhere else customer-facing - there's no id in
 * the URL to even get wrong here, which is itself the point: a customer
 * can only ever ask for their own balance.
 */
@RestController
@RequestMapping("/loyalty")
@Tag(name = "Loyalty", description = "Customer's own point balance")
@SecurityRequirement(name = "bearerAuth")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    public LoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @GetMapping("/me")
    @Operation(summary = "My current loyalty point balance")
    public LoyaltyBalanceResponse getBalance(@AuthenticationPrincipal Jwt jwt) {
        UUID customerId = UUID.fromString(jwt.getSubject());
        return new LoyaltyBalanceResponse(loyaltyService.getBalance(customerId));
    }

    @GetMapping("/me/transactions")
    @Operation(summary = "My loyalty point history, newest first")
    public List<LoyaltyTransactionResponse> getHistory(@AuthenticationPrincipal Jwt jwt) {
        UUID customerId = UUID.fromString(jwt.getSubject());
        return loyaltyService.getHistory(customerId).stream()
                .map(LoyaltyTransactionResponse::from)
                .toList();
    }
}
