package com.sublite.shared.domain;

/**
 * Two roles only: CUSTOMER (the default - no login of their own in this
 * project, they arrive with an id already) and ADMIN (staff managing plans,
 * the retention flow and loyalty rules). A real RBAC table
 * (roles/permissions, many-to-many) would be the next step if this ever
 * grew past a handful of fixed roles - not worth it for two.
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
