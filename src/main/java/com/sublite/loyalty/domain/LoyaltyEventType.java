package com.sublite.loyalty.domain;

/**
 * Only one event today (a successful renewal charge). Adding a real
 * "N consecutive months" streak bonus needs tracking consecutive
 * successful charges somewhere, which nothing in the project does yet -
 * a natural extension, not built in this pass.
 */
public enum LoyaltyEventType {
    PAYMENT_SUCCESS
}
