package com.sublite.security.domain;

/**
 * Deliberately the same exception (and the same message) whether the email
 * doesn't exist, belongs to a CUSTOMER with no password, or the password
 * just doesn't match - telling an attacker which case it was is exactly
 * the account-enumeration leak this type exists to avoid.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
