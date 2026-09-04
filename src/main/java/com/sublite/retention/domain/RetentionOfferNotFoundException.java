package com.sublite.retention.domain;

import java.util.UUID;

public class RetentionOfferNotFoundException extends RuntimeException {

    public RetentionOfferNotFoundException(UUID offerId) {
        super("No retention offer with id " + offerId);
    }
}
