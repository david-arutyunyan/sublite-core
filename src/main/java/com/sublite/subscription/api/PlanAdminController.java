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
public class PlanAdminController {

    private final PlanAdminService service;

    public PlanAdminController(PlanAdminService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
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
    public PlanPriceResponse addPrice(@PathVariable UUID id, @Valid @RequestBody AddPlanPriceRequest request) {
        PlanPrice price = service.setPrice(id, request.billingPeriod(), new Money(request.amount(), request.currency()));
        return PlanPriceResponse.from(price);
    }
}
