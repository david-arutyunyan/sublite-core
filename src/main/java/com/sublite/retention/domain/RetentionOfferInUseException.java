package com.sublite.retention.domain;

import java.util.UUID;

public class RetentionOfferInUseException extends RuntimeException {

    public RetentionOfferInUseException(UUID offerId) {
        super("Offer " + offerId + " is referenced by an active step and can't be deactivated - deactivate the step first");
    }
}
