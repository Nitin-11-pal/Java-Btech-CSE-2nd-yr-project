package com.realestate.util;

import com.realestate.model.User;

/**
 * Thread-safe Singleton managing the currently authenticated User in the Swing UI layer.
 * Note: Service classes never reference SessionManager; UI components pass the current User
 * explicitly as the actor parameter to service methods.
 */
// [RUBRIC: OOP] Singleton pattern for UI session state
public final class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    private User currentUser;

    private SessionManager() {
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public synchronized User getCurrentUser() {
        return currentUser;
    }

    public synchronized void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    public synchronized void logout() {
        this.currentUser = null;
    }
}
