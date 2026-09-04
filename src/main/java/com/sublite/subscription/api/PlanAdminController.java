package com.sublite.subscription.api;

import com.sublite.shared.api.dto.SetActiveRequest;
import com.sublite.shared.domain.Money;
import com.sublite.subscription.api.dto.AddPlanPriceRequest;
import com.sublite.subscription.api.dto.CreatePlanRequest;
import com.sublite.subscription.api.dto.PlanPriceResponse;
import com.sublite.subscription.api.dto.PlanResponse;
import com.sublite.subscription.application.PlanAdminService;
import com.sublite.subscription.domain.Plan;
import com.sublite.subscription.domain.PlanPrice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Under /admin - see SecurityConfig, gated to ROLE_ADMIN by URL prefix.
 */
@RestController
@RequestMapping("/admin/plans")
@Tag(name = "Admin: Plans", description = "Plan CRUD and price versioning")
@SecurityRequirement(name = "bearerAuth")
public class PlanAdminController {

    private final PlanAdminService service;

    public PlanAdminController(PlanAdminService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a plan with its first price",
            description = "A plan with no price isn't usable, so this creates both together.")
    public PlanResponse create(@Valid @RequestBody CreatePlanRequest request) {
        Plan plan = service.createPlan(
                request.code(),
                request.name(),
                request.description(),
                request.billingPeriod(),
                new Money(request.amount(), request.currency())
        );
        return PlanResponse.from(plan);
    }

    @GetMapping
    public List<PlanResponse> list() {
        return service.listPlans().stream().map(PlanResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PlanResponse get(@PathVariable UUID id) {
        return PlanResponse.from(service.getPlan(id));
    }

    @PatchMapping("/{id}/active")
    public PlanResponse setActive(@PathVariable UUID id, @Valid @RequestBody SetActiveRequest request) {
        return PlanResponse.from(service.setActive(id, request.active()));
    }

    @GetMapping("/{id}/prices")
    public List<PlanPriceResponse> prices(@PathVariable UUID id) {
        return service.priceHistory(id).stream().map(PlanPriceResponse::from).toList();
    }

    @PostMapping("/{id}/prices")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Version the price for a plan/billing period",
            description = "Closes whatever price is currently open-ended for this plan + billing period "
                    + "and opens a new one - existing subscriptions keep their own price, unaffected.")
    public PlanPriceResponse addPrice(@PathVariable UUID id, @Valid @RequestBody AddPlanPriceRequest request) {
        PlanPrice price = service.setPrice(id, request.billingPeriod(), new Money(request.amount(), request.currency()));
        return PlanPriceResponse.from(price);
    }
}
