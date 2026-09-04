package com.sublite.subscription.application;

import com.sublite.shared.domain.Money;
import com.sublite.subscription.domain.BillingPeriod;
import com.sublite.subscription.domain.Plan;
import com.sublite.subscription.domain.PlanCodeAlreadyExistsException;
import com.sublite.subscription.domain.PlanNotFoundException;
import com.sublite.subscription.domain.PlanPrice;
import com.sublite.subscription.infrastructure.PlanPriceRepository;
import com.sublite.subscription.infrastructure.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PlanAdminService {

    private final PlanRepository plans;
    private final PlanPriceRepository planPrices;
    private final Clock clock;

    public PlanAdminService(PlanRepository plans, PlanPriceRepository planPrices, Clock clock) {
        this.plans = plans;
        this.planPrices = planPrices;
        this.clock = clock;
    }

    /**
     * A plan with no price isn't usable, so create and price it in one
     * transaction - not two separate calls a caller could interleave a
     * crash between, leaving an unpriceable plan behind.
     */
    @Transactional
    public Plan createPlan(String code, String name, String description, BillingPeriod billingPeriod, Money price) {
        plans.findByCode(code).ifPresent(existing -> {
            throw new PlanCodeAlreadyExistsException(code);
        });

        Plan plan = plans.save(new Plan(UUID.randomUUID(), code, name, description, Instant.now(clock)));
        planPrices.save(new PlanPrice(UUID.randomUUID(), plan, billingPeriod, price, Instant.now(clock)));
        return plan;
    }

    /**
     * Versions the price for one plan + billing period: closes whatever
     * price is currently open-ended (closeCurrentPrice - a no-op the first
     * time a billing period gets a price) and inserts a new one. Existing
     * subscriptions keep pointing at their own PlanPrice row (see
     * Subscription.planPrice) - this never touches them.
     */
    @Transactional
    public PlanPrice setPrice(UUID planId, BillingPeriod billingPeriod, Money price) {
        Plan plan = requirePlan(planId);
        planPrices.closeCurrentPrice(planId, billingPeriod.name());
        return planPrices.save(new PlanPrice(UUID.randomUUID(), plan, billingPeriod, price, Instant.now(clock)));
    }

    @Transactional
    public Plan setActive(UUID planId, boolean active) {
        Plan plan = requirePlan(planId);
        if (active) {
            plan.activate(Instant.now(clock));
        } else {
            plan.deactivate(Instant.now(clock));
        }
        return plan;
    }

    @Transactional(readOnly = true)
    public List<Plan> listPlans() {
        return plans.findAll();
    }

    @Transactional(readOnly = true)
    public Plan getPlan(UUID planId) {
        return requirePlan(planId);
    }

    @Transactional(readOnly = true)
    public List<PlanPrice> priceHistory(UUID planId) {
        requirePlan(planId);
        return planPrices.findByPlanIdOrderByCreatedAtDesc(planId);
    }

    private Plan requirePlan(UUID planId) {
        return plans.findById(planId).orElseThrow(() -> new PlanNotFoundException(planId));
    }
}
