package com.realestate.model;

import java.util.Set;

/**
 * Abstract base class representing a system user with encapsulated fields and polymorphic behavior.
 */
// [RUBRIC: OOP] Abstract class with encapsulated fields and abstract polymorphic methods
public abstract class User {

    private int id;
    private String name;
    private String email;
    private String passwordHash;
    private String salt;
    private Role role;
    private boolean active;

    protected User(int id, String name, String email, String passwordHash, String salt, Role role, boolean active) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.active = active;
    }

    protected User(String name, String email, String passwordHash, String salt, Role role) {
        this(0, name, email, passwordHash, salt, role, true);
    }

    /**
     * Returns the CardLayout identifier of the dashboard corresponding to this user's role.
     *
     * @return dashboard card name
     */
    // [RUBRIC: OOP] Abstract method for runtime polymorphism
    public abstract String displayRoleDashboard();

    /**
     * Returns the immutable set of permission keys granted to this user's role.
     *
     * @return set of permission strings
     */
    // [RUBRIC: OOP] [RUBRIC: Collections] Abstract method returning a Set<String> of permissions
    public abstract Set<String> getPermissions();

    /**
     * Checks whether this user holds a specific permission key.
     *
     * @param permission permission identifier to check
     * @return true if granted
     */
    public boolean hasPermission(String permission) {
        return getPermissions().contains(permission);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return String.format("%s{id=%d, name='%s', email='%s', role=%s, active=%s}",
                getClass().getSimpleName(), id, name, email, role, active);
    }
}
