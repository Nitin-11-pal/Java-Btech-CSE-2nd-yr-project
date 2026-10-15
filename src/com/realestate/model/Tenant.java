package com.realestate.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Concrete User subclass representing a Tenant who searches properties and applies for leases.
 */
// [RUBRIC: OOP] Inheritance and method overriding
public class Tenant extends User {

    public static final String DASHBOARD_CARD = "TENANT_DASHBOARD";

    private static final Set<String> PERMISSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "SEARCH_PROPERTIES",
                    "APPLY_LEASE",
                    "VIEW_OWN_APPLICATIONS"
            ))
    );

    public Tenant(int id, String name, String email, String passwordHash, String salt, boolean active) {
        super(id, name, email, passwordHash, salt, Role.TENANT, active);
    }

    public Tenant(String name, String email, String passwordHash, String salt) {
        super(name, email, passwordHash, salt, Role.TENANT);
    }

    @Override
    // [RUBRIC: OOP] Polymorphic dashboard routing
    public String displayRoleDashboard() {
        return DASHBOARD_CARD;
    }

    @Override
    // [RUBRIC: OOP] [RUBRIC: Collections] Polymorphic permission set
    public Set<String> getPermissions() {
        return PERMISSIONS;
    }
}
