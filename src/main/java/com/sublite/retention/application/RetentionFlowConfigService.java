package com.sublite.retention.application;

import com.sublite.retention.domain.RetentionOffer;
import com.sublite.retention.domain.RetentionStep;
import com.sublite.retention.infrastructure.RetentionFlowCache;
import com.sublite.retention.infrastructure.RetentionStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cache-aside, read side: getActiveFlow() is called every time a customer
 * opens the cancellation flow, which is why it's cached - the flow
 * changes only when an admin edits it (evict(), called by the future
 * admin API in day 10), so most reads never touch Postgres at all.
 */
@Service
public class RetentionFlowConfigService {

    private final RetentionStepRepository steps;
    private final RetentionFlowCache cache;

    public RetentionFlowConfigService(RetentionStepRepository steps, RetentionFlowCache cache) {
        this.steps = steps;
        this.cache = cache;
    }

    /**
     * @Transactional here, not just on loadAndCache(): loadAndCache() is a
     * private method called via `this::` below, which never goes through
     * Spring's proxy on its own - the transaction has to be opened at this
     * public entry point so the lazy RetentionOffer load inside
     * loadAndCache() has an active session to work with, no matter which
     * caller invokes getActiveFlow() (a test calling it directly has no
     * transaction of its own to fall back on).
     */
    @Transactional(readOnly = true)
    public RetentionFlowConfig getActiveFlow() {
        return cache.get().orElseGet(this::loadAndCache);
    }

    /**
     * Called after any change to steps/offers - the next getActiveFlow()
     * call will miss the cache and rebuild it from Postgres.
     */
    public void evictCache() {
        cache.evict();
    }

    private RetentionFlowConfig loadAndCache() {
        RetentionFlowConfig config = new RetentionFlowConfig(
                steps.findByActiveTrueOrderByStepOrderAsc().stream()
                        .map(RetentionFlowConfigService::toView)
                        .toList()
        );
        cache.put(config);
        return config;
    }

    private static RetentionFlowConfig.StepView toView(RetentionStep step) {
        return new RetentionFlowConfig.StepView(
                step.getId(),
                step.getStepOrder(),
                step.getType(),
                step.getOffer() == null ? null : toView(step.getOffer())
        );
    }

    private static RetentionFlowConfig.OfferView toView(RetentionOffer offer) {
        return new RetentionFlowConfig.OfferView(offer.getId(), offer.getCode(), offer.getType(), offer.getParameters());
    }
}
