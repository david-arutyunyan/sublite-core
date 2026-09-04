package com.sublite.retention.domain;

public class RetentionOfferCodeAlreadyExistsException extends RuntimeException {

    public RetentionOfferCodeAlreadyExistsException(String code) {
        super("A retention offer with code '" + code + "' already exists");
    }
}
