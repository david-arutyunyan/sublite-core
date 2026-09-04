package com.sublite.retention.domain;

public class RetentionStepOrderAlreadyExistsException extends RuntimeException {

    public RetentionStepOrderAlreadyExistsException(int stepOrder) {
        super("A retention step at order " + stepOrder + " already exists");
    }
}
