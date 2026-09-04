package com.sublite.retention.application;

import com.sublite.retention.domain.RetentionOffer;
import com.sublite.retention.domain.RetentionOfferCodeAlreadyExistsException;
import com.sublite.retention.domain.RetentionOfferNotFoundException;
import com.sublite.retention.domain.RetentionOfferType;
import com.sublite.retention.domain.RetentionStep;
import com.sublite.retention.domain.RetentionStepNotFoundException;
import com.sublite.retention.domain.RetentionStepOrderAlreadyExistsException;
import com.sublite.retention.domain.RetentionStepRequiresOfferException;
import com.sublite.retention.domain.RetentionStepType;
import com.sublite.retention.infrastructure.RetentionOfferRepository;
import com.sublite.retention.infrastructure.RetentionStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every write here calls flowConfigService.evictCache() - exactly the
 * "future admin API in day 10" the cache-aside comment on
 * RetentionFlowConfigService.getActiveFlow() was written for. Without it,
 * an edit here wouldn't show up in the customer-facing flow until the
 * cache TTL (5 minutes, sublite.retention.flow-cache.ttl) expired on its
 * own.
 */
@Service
public class RetentionAdminService {

    private static final Logger log = LoggerFactory.getLogger(RetentionAdminService.class);

    private final RetentionOfferRepository offers;
    private final RetentionStepRepository steps;
    private final RetentionFlowConfigService flowConfigService;
    private final Clock clock;

    public RetentionAdminService(
            RetentionOfferRepository offers,
            RetentionStepRepository steps,
            RetentionFlowConfigService flowConfigService,
            Clock clock
    ) {
        this.offers = offers;
        this.steps = steps;
        this.flowConfigService = flowConfigService;
        this.clock = clock;
    }

    @Transactional
    public RetentionOffer createOffer(String code, RetentionOfferType type, Map<String, Object> parameters) {
        offers.findByCode(code).ifPresent(existing -> {
            throw new RetentionOfferCodeAlreadyExistsException(code);
        });

        RetentionOffer offer = offers.save(new RetentionOffer(UUID.randomUUID(), code, type, parameters, Instant.now(clock)));
        flowConfigService.evictCache();
        log.info("Retention offer created: offerId={}, code={}, type={}", offer.getId(), code, type);
        return offer;
    }

    @Transactional
    public RetentionStep createStep(int stepOrder, RetentionStepType type, UUID offerId) {
        if (steps.existsByStepOrder(stepOrder)) {
            throw new RetentionStepOrderAlreadyExistsException(stepOrder);
        }

        RetentionOffer offer = null;
        if (type == RetentionStepType.OFFER) {
            if (offerId == null) {
                throw new RetentionStepRequiresOfferException();
            }
            offer = offers.findById(offerId).orElseThrow(() -> new RetentionOfferNotFoundException(offerId));
        }

        RetentionStep step = steps.save(new RetentionStep(UUID.randomUUID(), stepOrder, type, offer, Instant.now(clock)));
        flowConfigService.evictCache();
        log.info("Retention step created: stepId={}, stepOrder={}, type={}, offerId={}", step.getId(), stepOrder, type, offerId);
        return step;
    }

    @Transactional
    public RetentionStep setStepActive(UUID stepId, boolean active) {
        RetentionStep step = steps.findById(stepId).orElseThrow(() -> new RetentionStepNotFoundException(stepId));
        step.setActive(active);
        flowConfigService.evictCache();
        log.info("Retention step {}: stepId={}", active ? "activated" : "deactivated", stepId);
        return step;
    }

    /**
     * Every offer, not just the ones in use by an active step - an admin
     * building the flow needs to see offers before wiring a step to one.
     */
    @Transactional(readOnly = true)
    public List<RetentionOffer> listOffers() {
        return offers.findAll();
    }

    /**
     * Every step including inactive ones, unlike
     * RetentionFlowConfigService.getActiveFlow() (which is what customers
     * go through) - admin needs to see and re-enable retired steps too.
     */
    @Transactional(readOnly = true)
    public List<RetentionStep> listSteps() {
        return steps.findAll();
    }
}
