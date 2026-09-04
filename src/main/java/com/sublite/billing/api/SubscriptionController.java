package com.sublite.billing.api;

import com.sublite.billing.api.dto.MySubscriptionResponse;
import com.sublite.billing.api.dto.PurchaseSubscriptionRequest;
import com.sublite.billing.application.SubscriptionPurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authenticated (see SecurityConfig's default "anyRequest().authenticated()"
 * - not /admin/**, so any logged-in customer or admin, not just ADMIN).
 * customerId always comes from the JWT subject, never a path/body
 * parameter - the alternative would let one customer read or buy against
 * another customer's id just by guessing/supplying it (IDOR).
 */
@RestController
@RequestMapping("/subscriptions")
@Tag(name = "Subscriptions", description = "Customer's own subscription - purchase and view")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionPurchaseService service;

    public SubscriptionController(SubscriptionPurchaseService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Buy a subscription",
            description = "Goes straight to ACTIVE with an immediate one-off charge for the first period - "
                    + "no trial. A declined/errored charge still creates the subscription; it just starts in "
                    + "GRACE_PERIOD instead of ACTIVE, same as a failed renewal would.")
    public MySubscriptionResponse purchase(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PurchaseSubscriptionRequest request) {
        UUID customerId = UUID.fromString(jwt.getSubject());
        return MySubscriptionResponse.from(service.purchase(customerId, request.planPriceId()));
    }

    @GetMapping("/me")
    @Operation(summary = "View my current subscription",
            description = "404 if the customer has never subscribed, or their only subscription is CANCELLED.")
    public MySubscriptionResponse getMine(@AuthenticationPrincipal Jwt jwt) {
        UUID customerId = UUID.fromString(jwt.getSubject());
        return MySubscriptionResponse.from(service.getMySubscription(customerId));
    }

    @PostMapping("/me/retry-payment")
    @Operation(summary = "Retry a failed payment now",
            description = "Only valid while the subscription is in GRACE_PERIOD - charges the same still-"
                    + "pending invoice the scheduler would eventually retry on its own, just immediately.")
    public MySubscriptionResponse retryPayment(@AuthenticationPrincipal Jwt jwt) {
        UUID customerId = UUID.fromString(jwt.getSubject());
        return MySubscriptionResponse.from(service.retryPayment(customerId));
    }
}
