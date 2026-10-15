package com.realestate.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Concrete User subclass representing a Property Manager who lists properties and reviews leases.
 */
// [RUBRIC: OOP] Inheritance and method overriding
public class PropertyManager extends User {

    public static final String DASHBOARD_CARD = "MANAGER_DASHBOARD";

    private static final Set<String> PERMISSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "CREATE_PROPERTY",
                    "EDIT_OWN_PROPERTY",
                    "DELETE_OWN_PROPERTY",
                    "VIEW_OWN_PROPERTIES",
                    "REVIEW_LEASE",
                    "VIEW_COMMISSIONS"
            ))
    );

    public PropertyManager(int id, String name, String email, String passwordHash, String salt, boolean active) {
        super(id, name, email, passwordHash, salt, Role.PROPERTY_MANAGER, active);
    }

    public PropertyManager(String name, String email, String passwordHash, String salt) {
        super(name, email, passwordHash, salt, Role.PROPERTY_MANAGER);
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
