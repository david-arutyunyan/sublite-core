package com.sublite.retention.api;

import com.sublite.retention.api.dto.CancellationAttemptResponse;
import com.sublite.retention.api.dto.SubmitReasonRequest;
import com.sublite.retention.application.RetentionCustomerService;
import com.sublite.retention.application.RetentionFlowConfig;
import com.sublite.retention.application.RetentionFlowService;
import com.sublite.retention.domain.CancellationAttempt;
import com.sublite.retention.domain.CancellationAttemptStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authenticated (any logged-in customer, not ROLE_ADMIN - see
 * SecurityConfig's default "anyRequest().authenticated()"). customerId
 * always comes from the JWT subject; RetentionCustomerService is what
 * actually verifies the subscription/attempt in the URL belongs to that
 * customer (see its own javadoc) - this controller never trusts the path
 * alone.
 *
 * /subscriptions/{id}/cancellation starts a flow (nested under the
 * subscription it cancels, matching billing.api.SubscriptionController's
 * /subscriptions/me); every later step addresses the attempt itself
 * under /cancellation/{attemptId}, since by then the subscription id in
 * the URL would add nothing a client doesn't already have from the
 * previous response.
 */
@RestController
@Tag(name = "Cancellation", description = "Customer-facing cancellation/retention flow")
@SecurityRequirement(name = "bearerAuth")
public class RetentionCustomerController {

    private final RetentionCustomerService service;
    private final RetentionFlowService flowService;

    public RetentionCustomerController(RetentionCustomerService service, RetentionFlowService flowService) {
        this.service = service;
        this.flowService = flowService;
    }

    @PostMapping("/subscriptions/{subscriptionId}/cancellation")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start the cancellation flow for my subscription")
    public CancellationAttemptResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID subscriptionId) {
        return toResponse(service.start(customerId(jwt), subscriptionId));
    }

    @GetMapping("/cancellation/{attemptId}")
    @Operation(summary = "See where a cancellation attempt is right now")
    public CancellationAttemptResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return toResponse(service.get(customerId(jwt), attemptId));
    }

    @PostMapping("/cancellation/{attemptId}/reason")
    @Operation(summary = "Answer the survey step")
    public CancellationAttemptResponse submitReason(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId, @Valid @RequestBody SubmitReasonRequest request) {
        return toResponse(service.submitReason(customerId(jwt), attemptId, request.reason()));
    }

    @PostMapping("/cancellation/{attemptId}/accept-offer")
    @Operation(summary = "Accept the current retention offer",
            description = "Applies the offer's effect immediately (pauses the subscription, awards points, ...) "
                    + "and ends the flow with status RETAINED.")
    public CancellationAttemptResponse acceptOffer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return toResponse(service.acceptCurrentOffer(customerId(jwt), attemptId));
    }

    @PostMapping("/cancellation/{attemptId}/decline-offer")
    @Operation(summary = "Decline the current retention offer and move to the next step")
    public CancellationAttemptResponse declineOffer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return toResponse(service.declineCurrentOffer(customerId(jwt), attemptId));
    }

    @PostMapping("/cancellation/{attemptId}/confirm")
    @Operation(summary = "Confirm the cancellation",
            description = "Only valid at a CONFIRMATION step. Actually cancels the subscription and ends the "
                    + "flow with status CANCELLED.")
    public CancellationAttemptResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return toResponse(service.confirmCancellation(customerId(jwt), attemptId));
    }

    private UUID customerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private CancellationAttemptResponse toResponse(CancellationAttempt attempt) {
        RetentionFlowConfig.StepView currentStep = attempt.getStatus() == CancellationAttemptStatus.IN_PROGRESS
                ? flowService.currentStep(attempt)
                : null;
        return CancellationAttemptResponse.from(attempt, currentStep);
    }
}
