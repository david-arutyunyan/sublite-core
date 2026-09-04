package com.sublite.retention.domain;

import java.util.UUID;

public class RetentionOfferNotActiveException extends RuntimeException {

    public RetentionOfferNotActiveException(UUID offerId) {
        super("Offer " + offerId + " is deactivated and can't be attached to a new step");
    }
}
