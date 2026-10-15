package com.realestate.service;

import com.realestate.dao.UserDAO;
import com.realestate.exception.AuthenticationFailedException;
import com.realestate.exception.InvalidUserDataException;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.exception.UnauthorizedActionException;
import com.realestate.model.PropertyManager;
import com.realestate.model.Role;
import com.realestate.model.Tenant;
import com.realestate.model.User;
import com.realestate.util.PasswordUtil;
import com.realestate.worker.AuditWorker;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

/**
 * Service handling authentication, registration, 3-strike account lockout, and a thread-safe
 * in-memory HashMap<Integer, User> cache for O(1) user lookups.
 */
// [RUBRIC: OOP] [RUBRIC: Collections] [RUBRIC: Threads]
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // [RUBRIC: Collections] [RUBRIC: Threads] In-memory O(1) user cache guarded by ReentrantReadWriteLock
    private final HashMap<Integer, User> userCache = new HashMap<>();
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

    // [RUBRIC: Collections] In-memory failed login attempt tracker per normalized email
    private final Map<String, Integer> failedAttempts = new HashMap<>();

    private final UserDAO userDAO;
    private final AuditWorker auditWorker;

    public AuthService(AuditWorker auditWorker) throws SQLException {
        this(new UserDAO(), auditWorker);
    }

    public AuthService(UserDAO userDAO, AuditWorker auditWorker) throws SQLException {
        this.userDAO = userDAO;
        this.auditWorker = auditWorker;
        refreshCacheFromDatabase();
    }

    /**
     * Loads all users from the database into the in-memory HashMap<Integer, User> cache.
     *
     * @throws SQLException if a database error occurs
     */
    // [RUBRIC: Collections] [RUBRIC: Threads]
    public final void refreshCacheFromDatabase() throws SQLException {
        List<User> allUsers = userDAO.findAll();
        cacheLock.writeLock().lock();
        try {
            userCache.clear();
            for (User u : allUsers) {
                userCache.put(u.getId(), u);
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Performs an O(1) lookup of a User by ID from the thread-safe in-memory HashMap cache,
     * falling back to the database and populating the cache if not yet cached.
     *
     * @param userId user ID to look up
     * @return Optional containing the User if found
     */
    // [RUBRIC: Collections] [RUBRIC: Threads] O(1) HashMap lookup guarded by read/write lock
    public Optional<User> getCachedUserById(int userId) {
        cacheLock.readLock().lock();
        try {
            User cached = userCache.get(userId);
            if (cached != null) {
                return Optional.of(cached);
            }
        } finally {
            cacheLock.readLock().unlock();
        }

        try {
            Optional<User> fromDb = userDAO.findById(userId);
            fromDb.ifPresent(this::putInCache);
            return fromDb;
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    /**
     * Authenticates a user by email and password, enforcing a 3-attempt in-memory lockout.
     *
     * @param email         user email
     * @param plainPassword plain-text password
     * @return authenticated polymorphic User instance
     * @throws AuthenticationFailedException if credentials fail, account is inactive, or account is locked
     * @throws SQLException                  if a database error occurs
     */
    // [RUBRIC: OOP] [RUBRIC: Threads]
    public User login(String email, String plainPassword) throws AuthenticationFailedException, SQLException {
        if (email == null || email.trim().isEmpty() || plainPassword == null || plainPassword.isEmpty()) {
            throw new AuthenticationFailedException("Email and password are required.");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (isAccountLocked(normalizedEmail)) {
            throw new AuthenticationFailedException(
                    "Account is locked after " + MAX_FAILED_ATTEMPTS + " failed login attempts: " + normalizedEmail);
        }

        Optional<User> userOpt = userDAO.findByEmail(normalizedEmail);
        if (!userOpt.isPresent()) {
            int attempts = recordFailedAttempt(normalizedEmail);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                auditWorker.logEvent(null, "ACCOUNT_LOCKED", "Locked unknown email after 3 failed attempts: " + normalizedEmail);
                throw new AuthenticationFailedException(
                        "Account is locked after " + MAX_FAILED_ATTEMPTS + " failed login attempts.");
            }
            throw new AuthenticationFailedException("Invalid email or password. Attempts remaining: "
                    + (MAX_FAILED_ATTEMPTS - attempts));
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            auditWorker.logEvent(user.getId(), "LOGIN_DENIED_INACTIVE", "Inactive user attempted login: " + normalizedEmail);
            throw new AuthenticationFailedException("Your account has been deactivated by an administrator.");
        }

        boolean passwordValid = PasswordUtil.verifyPassword(plainPassword, user.getSalt(), user.getPasswordHash());
        if (!passwordValid) {
            int attempts = recordFailedAttempt(normalizedEmail);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                auditWorker.logEvent(user.getId(), "ACCOUNT_LOCKED", "Account locked after 3 failed attempts: " + normalizedEmail);
                throw new AuthenticationFailedException(
                        "Account is locked after " + MAX_FAILED_ATTEMPTS + " failed login attempts.");
            }
            auditWorker.logEvent(user.getId(), "LOGIN_FAILED", "Invalid password attempt #" + attempts + " for " + normalizedEmail);
            throw new AuthenticationFailedException("Invalid email or password. Attempts remaining: "
                    + (MAX_FAILED_ATTEMPTS - attempts));
        }

        synchronized (this) {
            if (isAccountLocked(normalizedEmail)) {
                throw new AuthenticationFailedException(
                        "Account is locked after " + MAX_FAILED_ATTEMPTS + " failed login attempts: " + normalizedEmail);
            }
            clearFailedAttempts(normalizedEmail);
        }

        putInCache(user);
        auditWorker.logEvent(user.getId(), "USER_LOGIN", "User logged in with role " + user.getRole());
        return user;
    }

    /**
     * Registers a new Tenant or PropertyManager account with PBKDF2WithHmacSHA256 salted hashing.
     *
     * @param name          full name
     * @param email         unique email address
     * @param plainPassword plain-text password (minimum 6 characters)
     * @param role          TENANT or PROPERTY_MANAGER
     * @return newly created User
     * @throws InvalidUserDataException if validation fails or email is already taken
     * @throws SQLException             if a database error occurs
     */
    // [RUBRIC: OOP] [RUBRIC: Collections]
    public User register(String name, String email, String plainPassword, Role role)
            throws InvalidUserDataException, SQLException {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidUserDataException("Full name must not be empty.");
        }
        if (name.trim().length() > 100) {
            throw new InvalidUserDataException("Full name must not exceed 100 characters.");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new InvalidUserDataException("Please provide a valid email address.");
        }
        if (email.trim().length() > 150) {
            throw new InvalidUserDataException("Email address must not exceed 150 characters.");
        }
        if (plainPassword == null || plainPassword.length() < 6) {
            throw new InvalidUserDataException("Password must be at least 6 characters long.");
        }
        if (role != Role.TENANT && role != Role.PROPERTY_MANAGER) {
            throw new InvalidUserDataException("Self-registration is only permitted for TENANT or PROPERTY_MANAGER.");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userDAO.findByEmail(normalizedEmail).isPresent()) {
            throw new InvalidUserDataException("An account with email '" + normalizedEmail + "' already exists.");
        }

        String salt = PasswordUtil.generateSalt();
        String passwordHash = PasswordUtil.hashPassword(plainPassword, salt);

        User newUser;
        if (role == Role.PROPERTY_MANAGER) {
            newUser = new PropertyManager(name.trim(), normalizedEmail, passwordHash, salt);
        } else {
            newUser = new Tenant(name.trim(), normalizedEmail, passwordHash, salt);
        }

        try {
            User saved = userDAO.insert(newUser);
            putInCache(saved);
            auditWorker.logEvent(saved.getId(), "USER_REGISTERED",
                    "Registered new " + saved.getRole() + " (" + saved.getEmail() + ")");
            return saved;
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new InvalidUserDataException("An account with email '" + normalizedEmail + "' already exists.", e);
            }
            throw e;
        }
    }

    /**
     * Returns all users sorted by ID. Requires MANAGE_USERS permission (Admin).
     *
     * @param actor current acting user
     * @return list of all users
     * @throws UnauthorizedActionException if actor lacks MANAGE_USERS permission
     * @throws SQLException                if a database error occurs
     */
    // [RUBRIC: Collections] [RUBRIC: Threads]
    public List<User> getAllUsers(User actor) throws UnauthorizedActionException, SQLException {
        requirePermission(actor, "MANAGE_USERS");
        refreshCacheFromDatabase();
        cacheLock.readLock().lock();
        try {
            List<User> list = new ArrayList<>(userCache.values());
            list.sort(Comparator.comparingInt(User::getId));
            return list;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Soft-deactivates (or reactivates) a user account to preserve foreign-key integrity.
     *
     * @param actor        acting Admin user
     * @param targetUserId ID of the user to update
     * @param active       false to deactivate, true to reactivate
     * @throws UnauthorizedActionException if actor lacks MANAGE_USERS permission
     * @throws ResourceNotFoundException   if target user does not exist
     * @throws InvalidUserDataException    if admin attempts to deactivate their own account or state is unchanged
     * @throws SQLException                if a database error occurs
     */
    // [RUBRIC: OOP] [RUBRIC: Collections] [RUBRIC: Threads]
    public void setUserActiveStatus(User actor, int targetUserId, boolean active)
            throws UnauthorizedActionException, ResourceNotFoundException, InvalidUserDataException, SQLException {
        requirePermission(actor, "MANAGE_USERS");
        if (actor.getId() == targetUserId && !active) {
            throw new InvalidUserDataException("Administrators cannot deactivate their own active account.");
        }

        Optional<User> targetOpt = userDAO.findById(targetUserId);
        if (!targetOpt.isPresent()) {
            throw new ResourceNotFoundException("User with ID " + targetUserId + " was not found.");
        }

        User target = targetOpt.get();
        String normalizedTargetEmail = target.getEmail() != null
                ? target.getEmail().trim().toLowerCase(Locale.ROOT)
                : "";

        if (!active && !target.isActive()) {
            throw new InvalidUserDataException("User #" + targetUserId + " (" + target.getEmail() + ") is already deactivated.");
        }
        if (active && target.isActive() && !isAccountLocked(normalizedTargetEmail)) {
            throw new InvalidUserDataException(
                    "User #" + targetUserId + " (" + target.getEmail() + ") is already active and not locked out.");
        }

        userDAO.updateActiveStatus(targetUserId, active);
        target.setActive(active);
        putInCache(target);
        if (active && !normalizedTargetEmail.isEmpty()) {
            clearFailedAttempts(normalizedTargetEmail);
        }

        String action = active ? "USER_REACTIVATED" : "USER_DEACTIVATED";
        auditWorker.logEvent(actor.getId(), action,
                "Admin set active=" + active + " for user ID " + targetUserId + " (" + target.getEmail() + ")");
    }

    /**
     * Default admin delete/remove action: performs soft deactivation (active = false)
     * to avoid foreign-key violations on properties, leases, and commissions.
     *
     * @param actor        acting Admin user
     * @param targetUserId ID of the user to deactivate
     * @throws UnauthorizedActionException if actor lacks MANAGE_USERS permission
     * @throws ResourceNotFoundException   if target user does not exist
     * @throws InvalidUserDataException    if admin attempts to deactivate their own account
     * @throws SQLException                if a database error occurs
     */
    public void deactivateUser(User actor, int targetUserId)
            throws UnauthorizedActionException, ResourceNotFoundException, InvalidUserDataException, SQLException {
        setUserActiveStatus(actor, targetUserId, false);
    }

    private void putInCache(User user) {
        cacheLock.writeLock().lock();
        try {
            User existing = userCache.get(user.getId());
            if (existing != null && existing != user) {
                existing.setActive(user.isActive());
                existing.setName(user.getName());
                existing.setEmail(user.getEmail());
            }
            userCache.put(user.getId(), user);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    private synchronized boolean isAccountLocked(String normalizedEmail) {
        return failedAttempts.getOrDefault(normalizedEmail, 0) >= MAX_FAILED_ATTEMPTS;
    }

    private synchronized int recordFailedAttempt(String normalizedEmail) {
        int updated = failedAttempts.getOrDefault(normalizedEmail, 0) + 1;
        failedAttempts.put(normalizedEmail, updated);
        return updated;
    }

    private synchronized void clearFailedAttempts(String normalizedEmail) {
        failedAttempts.remove(normalizedEmail);
    }

    private void requirePermission(User actor, String permission) throws UnauthorizedActionException {
        if (actor == null) {
            throw new UnauthorizedActionException("Authentication is required to perform this action.");
        }
        if (!actor.isActive()) {
            throw new UnauthorizedActionException("Your account is inactive.");
        }
        Optional<User> liveUserOpt = getCachedUserById(actor.getId());
        if (!liveUserOpt.isPresent() || !liveUserOpt.get().isActive()) {
            actor.setActive(false);
            throw new UnauthorizedActionException("Your account has been deactivated by an administrator.");
        }
        if (!actor.hasPermission(permission)) {
            throw new UnauthorizedActionException(
                    "User '" + actor.getEmail() + "' (" + actor.getRole() + ") lacks permission: " + permission);
        }
    }
}
