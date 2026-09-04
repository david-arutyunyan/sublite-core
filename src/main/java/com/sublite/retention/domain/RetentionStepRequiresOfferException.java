package com.sublite.retention.domain;

public class RetentionStepRequiresOfferException extends RuntimeException {

    public RetentionStepRequiresOfferException() {
        super("A step of type OFFER must reference an existing retention offer");
    }
}
