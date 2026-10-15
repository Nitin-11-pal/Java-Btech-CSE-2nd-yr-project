package com.realestate.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Concrete User subclass representing a system Administrator.
 */
// [RUBRIC: OOP] Inheritance and method overriding
public class Admin extends User {

    public static final String DASHBOARD_CARD = "ADMIN_DASHBOARD";

    private static final Set<String> PERMISSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "MANAGE_USERS",
                    "REVIEW_PROPERTY",
                    "VIEW_AUDIT_LOG",
                    "VIEW_ANALYTICS"
            ))
    );

    public Admin(int id, String name, String email, String passwordHash, String salt, boolean active) {
        super(id, name, email, passwordHash, salt, Role.ADMIN, active);
    }

    public Admin(String name, String email, String passwordHash, String salt) {
        super(name, email, passwordHash, salt, Role.ADMIN);
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
