package com.sublite.subscription.domain;

public class PlanCodeAlreadyExistsException extends RuntimeException {

    public PlanCodeAlreadyExistsException(String code) {
        super("A plan with code '" + code + "' already exists");
    }
}
