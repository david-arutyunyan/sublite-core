package com.sublite.subscription.api;

import com.sublite.subscription.api.dto.PublicPlanResponse;
import com.sublite.subscription.api.dto.PublicPriceResponse;
import com.sublite.subscription.application.PlanCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public (see SecurityConfig) - browsing plans doesn't need an account,
 * same as any storefront. Distinct from PlanAdminController: this only
 * ever shows active plans with their current price, nothing more.
 */
@RestController
@RequestMapping("/plans")
@Tag(name = "Plans", description = "Public plan catalog")
public class PlanController {

    private final PlanCatalogService service;

    public PlanController(PlanCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List plans available to subscribe to")
    public List<PublicPlanResponse> list() {
        return service.listActivePlans().stream()
                .map(plan -> PublicPlanResponse.from(
                        plan,
                        service.currentPrices(plan.getId()).stream().map(PublicPriceResponse::from).toList()
                ))
                .toList();
    }
}
