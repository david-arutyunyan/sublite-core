package com.sublite.subscription.infrastructure;

import com.sublite.subscription.domain.PlanPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PlanPriceRepository extends JpaRepository<PlanPrice, UUID> {

    List<PlanPrice> findByPlanIdOrderByCreatedAtDesc(UUID planId);

    /**
     * The other half of price versioning (see PlanAdminService.setPrice()):
     * closes whichever price is currently open-ended for this plan +
     * billing period, so the EXCLUDE constraint on plan_prices doesn't
     * reject the new row for overlapping it. upper_inf(), not
     * "upper(valid_period) IS NULL" - that's the correct/documented way to
     * test for an unbounded range in Postgres.
     *
     * now() here (not a bound parameter) is deliberate: Postgres evaluates
     * now() once per transaction, not once per statement, so this UPDATE
     * and the INSERT that follows in the same @Transactional method (which
     * leaves valid_period unset, letting the V8 column default
     * tstzrange(now(), null) apply) always agree on the exact instant -
     * no gap, no overlap, no need to thread a timestamp through Java to
     * keep the two statements in sync.
     */
    @Modifying
    @Query(value = """
            UPDATE subscription.plan_prices
            SET valid_period = tstzrange(lower(valid_period), now())
            WHERE plan_id = :planId AND billing_period = :billingPeriod AND upper_inf(valid_period)
            """, nativeQuery = true)
    int closeCurrentPrice(@Param("planId") UUID planId, @Param("billingPeriod") String billingPeriod);

    /**
     * The prices a customer should actually be offered: whichever one is
     * open-ended right now, per billing period (usually one row for
     * MONTHLY and one for YEARLY). Same upper_inf() test as
     * closeCurrentPrice() - see its note on why not "IS NULL".
     */
    @Query(value = "SELECT * FROM subscription.plan_prices WHERE plan_id = :planId AND upper_inf(valid_period)",
            nativeQuery = true)
    List<PlanPrice> findCurrentPrices(@Param("planId") UUID planId);
}
