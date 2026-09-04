package com.sublite.subscription.application;

import com.sublite.subscription.domain.Plan;
import com.sublite.subscription.domain.PlanPrice;
import com.sublite.subscription.infrastructure.PlanPriceRepository;
import com.sublite.subscription.infrastructure.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The customer-facing read side of plans - active-only, current price
 * only. Deliberately separate from PlanAdminService: that one shows admins
 * everything (inactive plans, full price history), this one shows
 * customers only what they should be able to buy right now.
 */
@Service
public class PlanCatalogService {

    private final PlanRepository plans;
    private final PlanPriceRepository planPrices;

    public PlanCatalogService(PlanRepository plans, PlanPriceRepository planPrices) {
        this.plans = plans;
        this.planPrices = planPrices;
    }

    @Transactional(readOnly = true)
    public List<Plan> listActivePlans() {
        return plans.findByActiveTrue();
    }

    /**
     * One query per plan - fine at the scale a handful of hand-configured
     * plans implies. Would batch-fetch (a single IN-query keyed by plan
     * id, grouped in memory) if this ever needed to list hundreds of plans.
     */
    @Transactional(readOnly = true)
    public List<PlanPrice> currentPrices(UUID planId) {
        return planPrices.findCurrentPrices(planId);
    }
}
