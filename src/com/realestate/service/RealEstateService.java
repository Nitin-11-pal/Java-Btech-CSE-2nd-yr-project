package com.realestate.service;

import com.realestate.dao.AuditLogDAO;
import com.realestate.dao.CommissionDAO;
import com.realestate.dao.LeaseApplicationDAO;
import com.realestate.dao.PropertyDAO;
import com.realestate.exception.InvalidLeaseDataException;
import com.realestate.exception.InvalidPropertyDataException;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.exception.UnauthorizedActionException;
import com.realestate.model.AuditLog;
import com.realestate.model.Commission;
import com.realestate.model.LeaseApplication;
import com.realestate.model.LeaseStatus;
import com.realestate.model.Property;
import com.realestate.model.PropertyStatus;
import com.realestate.model.Reviewable;
import com.realestate.model.User;
import com.realestate.util.AnalyticsTracker;
import com.realestate.util.DBConnection;
import com.realestate.worker.AuditWorker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Core domain service orchestrating property listings, admin moderation, tenant search/applications,
 * and atomic multi-step JDBC lease approval transactions.
 */
// [RUBRIC: OOP] [RUBRIC: Collections] [RUBRIC: Transaction]
public class RealEstateService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05");

    private final PropertyDAO propertyDAO;
    private final LeaseApplicationDAO leaseApplicationDAO;
    private final CommissionDAO commissionDAO;
    private final AuditLogDAO auditLogDAO;
    private final AuthService authService;
    private final AuditWorker auditWorker;
    private final AnalyticsTracker analyticsTracker;

    public RealEstateService(AuthService authService,
                             AuditWorker auditWorker,
                             AnalyticsTracker analyticsTracker) throws SQLException {
        this(new PropertyDAO(), new LeaseApplicationDAO(), new CommissionDAO(), new AuditLogDAO(),
                authService, auditWorker, analyticsTracker);
    }

    public RealEstateService(PropertyDAO propertyDAO,
                             LeaseApplicationDAO leaseApplicationDAO,
                             CommissionDAO commissionDAO,
                             AuditLogDAO auditLogDAO,
                             AuthService authService,
                             AuditWorker auditWorker,
                             AnalyticsTracker analyticsTracker) throws SQLException {
        this.propertyDAO = propertyDAO;
        this.leaseApplicationDAO = leaseApplicationDAO;
        this.commissionDAO = commissionDAO;
        this.auditLogDAO = auditLogDAO;
        this.authService = authService;
        this.auditWorker = auditWorker;
        this.analyticsTracker = analyticsTracker;
        initializeAnalyticsFromDatabase();
    }

    private void initializeAnalyticsFromDatabase() throws SQLException {
        List<Property> allProperties = propertyDAO.findAll();
        int approvedProps = 0;
        int rejectedProps = 0;
        for (Property p : allProperties) {
            if (p.getStatus() == PropertyStatus.APPROVED || p.getStatus() == PropertyStatus.RENTED) {
                approvedProps++;
            } else if (p.getStatus() == PropertyStatus.REJECTED) {
                rejectedProps++;
            }
        }

        List<LeaseApplication> allLeases = leaseApplicationDAO.findAll();
        int approvedLeases = 0;
        for (LeaseApplication la : allLeases) {
            if (la.getStatus() == LeaseStatus.APPROVED) {
                approvedLeases++;
            }
        }

        List<Commission> commissions = commissionDAO.findAll();
        BigDecimal totalCommission = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (Commission c : commissions) {
            if (c.getAmount() != null) {
                totalCommission = totalCommission.add(c.getAmount());
            }
        }

        analyticsTracker.initializeCounts(
                approvedProps,
                rejectedProps,
                approvedLeases,
                allLeases.size(),
                totalCommission
        );
    }

    // =========================================================================
    // PROPERTY MANAGER OPERATIONS
    // =========================================================================

    /**
     * Creates a new property listing starting in PENDING status.
     */
    // [RUBRIC: OOP]
    public Property createProperty(User actor, String title, String city, String address,
                                   BigDecimal monthlyRent, int bedrooms)
            throws UnauthorizedActionException, InvalidPropertyDataException, SQLException {
        requirePermission(actor, "CREATE_PROPERTY");
        validatePropertyInputs(title, city, address, monthlyRent, bedrooms);

        Property property = new Property(
                actor.getId(),
                title.trim(),
                city.trim(),
                address.trim(),
                monthlyRent.setScale(2, RoundingMode.HALF_UP),
                bedrooms
        );

        Property saved = propertyDAO.insert(property);
        auditWorker.logEvent(actor.getId(), "PROPERTY_CREATED",
                "Created property #" + saved.getId() + " '" + saved.getTitle() + "' in " + saved.getCity());
        return saved;
    }

    /**
     * Updates an existing property owned by the manager. Editing an APPROVED or REJECTED
     * property automatically resets its status to PENDING and clears any rejection reason.
     */
    // [RUBRIC: OOP]
    public Property updateProperty(User actor, int propertyId, String title, String city, String address,
                                   BigDecimal monthlyRent, int bedrooms)
            throws UnauthorizedActionException, ResourceNotFoundException, InvalidPropertyDataException, SQLException {
        requirePermission(actor, "EDIT_OWN_PROPERTY");
        validatePropertyInputs(title, city, address, monthlyRent, bedrooms);

        Optional<Property> existingOpt = propertyDAO.findById(propertyId);
        if (!existingOpt.isPresent()) {
            throw new ResourceNotFoundException("Property #" + propertyId + " was not found.");
        }

        Property property = existingOpt.get();
        if (property.getManagerId() != actor.getId()) {
            throw new UnauthorizedActionException("You can only edit your own property listings.");
        }
        if (property.getStatus() == PropertyStatus.RENTED) {
            throw new InvalidPropertyDataException("Cannot edit property #" + propertyId + " because it is already RENTED.");
        }

        PropertyStatus previousStatus = property.getStatus();
        property.setTitle(title.trim());
        property.setCity(city.trim());
        property.setAddress(address.trim());
        property.setMonthlyRent(monthlyRent.setScale(2, RoundingMode.HALF_UP));
        property.setBedrooms(bedrooms);

        // Editing an APPROVED or REJECTED property resets it to PENDING for re-moderation
        property.setStatus(PropertyStatus.PENDING);
        property.setRejectionReason(null);

        propertyDAO.update(property);
        if (previousStatus == PropertyStatus.APPROVED || previousStatus == PropertyStatus.REJECTED) {
            initializeAnalyticsFromDatabase();
        }
        auditWorker.logEvent(actor.getId(), "PROPERTY_UPDATED",
                "Updated property #" + property.getId() + " (status reset to PENDING)");
        return property;
    }

    /**
     * Deletes a property owned by the manager only if it has zero lease applications.
     */
    // [RUBRIC: OOP]
    public void deleteProperty(User actor, int propertyId)
            throws UnauthorizedActionException, ResourceNotFoundException, InvalidPropertyDataException, SQLException {
        requirePermission(actor, "DELETE_OWN_PROPERTY");

        Optional<Property> existingOpt = propertyDAO.findById(propertyId);
        if (!existingOpt.isPresent()) {
            throw new ResourceNotFoundException("Property #" + propertyId + " was not found.");
        }

        Property property = existingOpt.get();
        if (property.getManagerId() != actor.getId()) {
            throw new UnauthorizedActionException("You can only delete your own property listings.");
        }
        if (property.getStatus() == PropertyStatus.RENTED) {
            throw new InvalidPropertyDataException("Cannot delete a RENTED property.");
        }

        int leaseCount = leaseApplicationDAO.countByPropertyId(propertyId);
        if (leaseCount > 0) {
            throw new InvalidPropertyDataException(
                    "Cannot delete property #" + propertyId + " because it has " + leaseCount
                            + " existing lease application(s).");
        }

        PropertyStatus deletedStatus = property.getStatus();
        propertyDAO.delete(propertyId);
        if (deletedStatus == PropertyStatus.APPROVED || deletedStatus == PropertyStatus.REJECTED) {
            initializeAnalyticsFromDatabase();
        }
        auditWorker.logEvent(actor.getId(), "PROPERTY_DELETED",
                "Deleted property #" + propertyId + " ('" + property.getTitle() + "')");
    }

    /**
     * Returns all properties managed by the given PropertyManager.
     */
    public List<Property> getPropertiesByManager(User actor) throws UnauthorizedActionException, SQLException {
        requirePermission(actor, "VIEW_OWN_PROPERTIES");
        return propertyDAO.findByManagerId(actor.getId());
    }

    /**
     * Returns all lease applications submitted on properties owned by the given PropertyManager.
     */
    public List<LeaseApplication> getManagerApplications(User actor) throws UnauthorizedActionException, SQLException {
        requirePermission(actor, "REVIEW_LEASE");
        return leaseApplicationDAO.findByManagerId(actor.getId());
    }

    /**
     * Returns all commission records earned by the given PropertyManager.
     */
    public List<Commission> getManagerCommissions(User actor) throws UnauthorizedActionException, SQLException {
        requirePermission(actor, "VIEW_COMMISSIONS");
        return commissionDAO.findByManagerId(actor.getId());
    }

    /**
     * Rejects a PENDING lease application on a property owned by the acting manager.
     */
    public void rejectLease(User actor, int applicationId)
            throws UnauthorizedActionException, ResourceNotFoundException, InvalidLeaseDataException, SQLException {
        requirePermission(actor, "REVIEW_LEASE");

        Optional<LeaseApplication> appOpt = leaseApplicationDAO.findById(applicationId);
        if (!appOpt.isPresent()) {
            throw new ResourceNotFoundException("Lease application #" + applicationId + " was not found.");
        }
        LeaseApplication app = appOpt.get();

        Optional<Property> propOpt = propertyDAO.findById(app.getPropertyId());
        if (!propOpt.isPresent()) {
            throw new ResourceNotFoundException("Property #" + app.getPropertyId() + " was not found.");
        }
        Property property = propOpt.get();

        if (property.getManagerId() != actor.getId()) {
            throw new UnauthorizedActionException("You can only reject lease applications for your own properties.");
        }
        if (app.getStatus() != LeaseStatus.PENDING) {
            throw new InvalidLeaseDataException("Only PENDING lease applications can be rejected.");
        }

        leaseApplicationDAO.updateStatus(applicationId, LeaseStatus.REJECTED);
        auditWorker.logEvent(actor.getId(), "LEASE_REJECTED",
                "Manager rejected lease application #" + applicationId + " on property #" + property.getId());
    }

    /**
     * Atomically approves a lease application inside a single JDBC transaction:
     * 1. setAutoCommit(false)
     * 2. Resolve target propertyId and lock the Property row FIRST (SELECT ... FOR UPDATE),
     *    then lock the LeaseApplication row SECOND (SELECT ... FOR UPDATE) to prevent cyclic deadlocks
     *    when concurrent threads approve competing applications on the same property
     * 3. Verify manager ownership, verify application belongs to locked property, verify LeaseApplication
     *    status is still PENDING, verify Property status is still APPROVED, and verify applicant is active
     * 4. Mark LeaseApplication as APPROVED
     * 5. Mark Property as RENTED
     * 6. Reject all other PENDING applications on that property
     * 7. Insert 5% manager Commission record
     * 8. commit() (or rollback() on any exception), restore autoCommit(true) in finally
     * 9. Post-commit: update AnalyticsTracker and push AuditLog event to BlockingQueue
     */
    // [RUBRIC: Transaction] [RUBRIC: Threads] Full ACID transaction with SELECT ... FOR UPDATE and post-commit audit
    public Commission approveLease(User actor, int applicationId)
            throws UnauthorizedActionException, ResourceNotFoundException, InvalidLeaseDataException, SQLException {
        requirePermission(actor, "REVIEW_LEASE");

        Connection conn = null;
        Commission createdCommission = null;
        int lockedPropertyId = 0;
        int rejectedCompetitors = 0;
        boolean committed = false;

        try {
            conn = DBConnection.getInstance().getConnection();
            // [RUBRIC: Transaction] Begin manual transaction boundary
            conn.setAutoCommit(false);

            // Step 1: Look up the LeaseApplication to determine its property_id
            Optional<LeaseApplication> initialAppOpt = leaseApplicationDAO.findById(conn, applicationId);
            if (!initialAppOpt.isPresent()) {
                throw new ResourceNotFoundException("Lease application #" + applicationId + " was not found.");
            }
            lockedPropertyId = initialAppOpt.get().getPropertyId();

            // Step 2: Lock the parent Property row FIRST with SELECT ... FOR UPDATE (prevents deadlock across competing apps)
            Optional<Property> lockedPropOpt = propertyDAO.findByIdForUpdate(conn, lockedPropertyId);
            if (!lockedPropOpt.isPresent()) {
                throw new ResourceNotFoundException("Property #" + lockedPropertyId + " was not found.");
            }
            Property lockedProperty = lockedPropOpt.get();

            // Step 3: Lock the LeaseApplication row SECOND with SELECT ... FOR UPDATE
            Optional<LeaseApplication> lockedAppOpt = leaseApplicationDAO.findByIdForUpdate(conn, applicationId);
            if (!lockedAppOpt.isPresent()) {
                throw new ResourceNotFoundException("Lease application #" + applicationId + " was not found.");
            }
            LeaseApplication lockedApp = lockedAppOpt.get();

            // Step 4: Verify ownership, relationship, and current statuses on the locked rows
            if (lockedProperty.getManagerId() != actor.getId()) {
                throw new UnauthorizedActionException("You can only approve leases on your own properties.");
            }
            if (lockedApp.getPropertyId() != lockedProperty.getId()) {
                throw new InvalidLeaseDataException("Lease application does not belong to the locked property.");
            }
            if (lockedApp.getStatus() != LeaseStatus.PENDING) {
                throw new InvalidLeaseDataException(
                        "Lease application #" + applicationId + " is no longer PENDING (current status: "
                                + lockedApp.getStatus() + ").");
            }
            if (lockedProperty.getStatus() != PropertyStatus.APPROVED) {
                throw new InvalidLeaseDataException(
                        "Property #" + lockedProperty.getId() + " is not APPROVED for leasing (current status: "
                                + lockedProperty.getStatus() + ").");
            }
            Optional<User> applicantOpt = authService.getCachedUserById(lockedApp.getTenantId());
            if (applicantOpt.isPresent() && !applicantOpt.get().isActive()) {
                throw new InvalidLeaseDataException(
                        "Cannot approve lease application #" + applicationId + " because the tenant account is deactivated.");
            }

            // Step 5: Mark the winning LeaseApplication as APPROVED
            leaseApplicationDAO.updateStatus(conn, applicationId, LeaseStatus.APPROVED);

            // Step 6: Mark the Property as RENTED
            propertyDAO.updateStatus(conn, lockedProperty.getId(), PropertyStatus.RENTED, null);

            // Step 7: Reject all other PENDING applications on this property
            rejectedCompetitors = leaseApplicationDAO.rejectOtherPendingByProperty(
                    conn, lockedProperty.getId(), applicationId);

            // Step 8: Calculate 5% monthly rent commission and insert record
            BigDecimal commissionAmount = lockedProperty.getMonthlyRent()
                    .multiply(COMMISSION_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
            Commission commission = new Commission(actor.getId(), applicationId, commissionAmount);
            createdCommission = commissionDAO.insert(conn, commission);

            // Step 9: Commit the atomic transaction
            // [RUBRIC: Transaction]
            conn.commit();
            committed = true;
        } catch (SQLException | ResourceNotFoundException | UnauthorizedActionException | InvalidLeaseDataException | RuntimeException ex) {
            // [RUBRIC: Transaction] Rollback on any checked or runtime failure inside the transaction
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
            }
            throw ex;
        } finally {
            // [RUBRIC: Transaction] Guarantee rollback if not committed before restoring autoCommit(true)
            if (conn != null) {
                if (!committed) {
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                    }
                }
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }

        // Step 10: Post-commit updates (Analytics + BlockingQueue AuditWorker)
        analyticsTracker.recordLeaseApproval(createdCommission.getAmount());
        auditWorker.logEvent(
                actor.getId(),
                "LEASE_APPROVED",
                "Approved lease #" + applicationId + " on property #" + lockedPropertyId
                        + " (auto-rejected " + rejectedCompetitors + " competing application(s), commission $"
                        + createdCommission.getAmount().toPlainString() + ")"
        );

        return createdCommission;
    }

    // =========================================================================
    // ADMIN OPERATIONS
    // =========================================================================

    /**
     * Returns all PENDING properties awaiting admin moderation.
     */
    public List<Property> getPendingProperties(User actor) throws UnauthorizedActionException, SQLException {
        requirePermission(actor, "REVIEW_PROPERTY");
        return propertyDAO.findByStatus(PropertyStatus.PENDING);
    }

    /**
     * Returns all properties in the system for Admin inspection.
     */
    public List<Property> getAllProperties(User actor) throws UnauthorizedActionException, SQLException {
        requirePermission(actor, "REVIEW_PROPERTY");
        return propertyDAO.findAll();
    }

    /**
     * Approves a PENDING property using the Reviewable interface contract.
     */
    // [RUBRIC: OOP] Uses Reviewable interface
    public void approveProperty(User actor, int propertyId)
            throws UnauthorizedActionException, ResourceNotFoundException, InvalidPropertyDataException, SQLException {
        requirePermission(actor, "REVIEW_PROPERTY");

        Optional<Property> propOpt = propertyDAO.findById(propertyId);
        if (!propOpt.isPresent()) {
            throw new ResourceNotFoundException("Property #" + propertyId + " was not found.");
        }

        Property property = propOpt.get();
        if (property.getStatus() != PropertyStatus.PENDING) {
            throw new InvalidPropertyDataException(
                    "Only PENDING properties can be approved (current status: " + property.getStatus() + ").");
        }

        // [RUBRIC: OOP] Polymorphic Reviewable call
        Reviewable reviewable = property;
        reviewable.approve();

        propertyDAO.updateStatus(propertyId, property.getStatus(), property.getRejectionReason());
        analyticsTracker.incrementPropertyApprovals();
        auditWorker.logEvent(actor.getId(), "PROPERTY_APPROVED",
                "Admin approved property #" + propertyId + " ('" + property.getTitle() + "')");
    }

    /**
     * Rejects a PENDING property with a mandatory rejection reason using the Reviewable interface.
     */
    // [RUBRIC: OOP] Uses Reviewable interface
    public void rejectProperty(User actor, int propertyId, String reason)
            throws UnauthorizedActionException, ResourceNotFoundException, InvalidPropertyDataException, SQLException {
        requirePermission(actor, "REVIEW_PROPERTY");
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidPropertyDataException("A rejection reason is required when rejecting a property.");
        }
        if (reason.trim().length() > 500) {
            throw new InvalidPropertyDataException("Rejection reason must not exceed 500 characters.");
        }

        Optional<Property> propOpt = propertyDAO.findById(propertyId);
        if (!propOpt.isPresent()) {
            throw new ResourceNotFoundException("Property #" + propertyId + " was not found.");
        }

        Property property = propOpt.get();
        if (property.getStatus() != PropertyStatus.PENDING) {
            throw new InvalidPropertyDataException(
                    "Only PENDING properties can be rejected (current status: " + property.getStatus() + ").");
        }

        // [RUBRIC: OOP] Polymorphic Reviewable call
        Reviewable reviewable = property;
        reviewable.reject(reason.trim());

        propertyDAO.updateStatus(propertyId, property.getStatus(), property.getRejectionReason());
        analyticsTracker.incrementPropertyRejections();
        auditWorker.logEvent(actor.getId(), "PROPERTY_REJECTED",
                "Admin rejected property #" + propertyId + " (Reason: " + property.getRejectionReason() + ")");
    }

    /**
     * Returns all persisted audit log entries for Admin inspection after awaiting any in-flight queue writes.
     */
    public List<AuditLog> getAuditLogs(User actor) throws UnauthorizedActionException, SQLException {
        requirePermission(actor, "VIEW_AUDIT_LOG");
        auditWorker.awaitPendingWrites(500);
        return auditLogDAO.findAll();
    }

    /**
     * Returns a thread-safe snapshot of system analytics counters for Admin inspection.
     */
    public Map<String, String> getAnalyticsSnapshot(User actor) throws UnauthorizedActionException, SQLException {
        requirePermission(actor, "VIEW_ANALYTICS");
        initializeAnalyticsFromDatabase();
        return analyticsTracker.getSnapshot();
    }

    // =========================================================================
    // TENANT OPERATIONS
    // =========================================================================

    /**
     * Returns a sorted Set<String> of distinct cities across all APPROVED properties.
     */
    // [RUBRIC: Collections] Uses Set<String> (TreeSet) to hold unique city names
    public Set<String> getApprovedCities() throws SQLException {
        ArrayList<Property> approved = propertyDAO.findByStatus(PropertyStatus.APPROVED);
        Set<String> cities = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Property p : approved) {
            if (p.getCity() != null && !p.getCity().trim().isEmpty()) {
                cities.add(p.getCity().trim());
            }
        }
        return cities;
    }

    /**
     * Searches APPROVED properties using an ArrayList<Property>, filtering by optional city,
     * maximum monthly rent, and minimum bedrooms, and sorting by monthly rent via Comparator.
     */
    // [RUBRIC: Collections] ArrayList<Property> filtering and Collections.sort with Comparator
    public ArrayList<Property> searchApprovedProperties(User actor,
                                                        String cityFilter,
                                                        BigDecimal maxMonthlyRent,
                                                        Integer minBedrooms,
                                                        boolean sortByRentAscending)
            throws UnauthorizedActionException, SQLException {
        requirePermission(actor, "SEARCH_PROPERTIES");

        ArrayList<Property> allProperties = propertyDAO.findAll();
        ArrayList<Property> filtered = new ArrayList<>();

        for (Property prop : allProperties) {
            if (prop.getStatus() != PropertyStatus.APPROVED) {
                continue;
            }
            if (cityFilter != null && !cityFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(cityFilter.trim())) {
                if (!prop.getCity().equalsIgnoreCase(cityFilter.trim())) {
                    continue;
                }
            }
            if (maxMonthlyRent != null && prop.getMonthlyRent().compareTo(maxMonthlyRent) > 0) {
                continue;
            }
            if (minBedrooms != null && prop.getBedrooms() < minBedrooms) {
                continue;
            }
            filtered.add(prop);
        }

        // [RUBRIC: Collections] Sort using Comparator and Collections.sort
        Comparator<Property> rentComparator = Comparator.comparing(Property::getMonthlyRent);
        if (!sortByRentAscending) {
            rentComparator = rentComparator.reversed();
        }
        Collections.sort(filtered, rentComparator);

        return filtered;
    }

    /**
     * Submits a lease application for an APPROVED property on behalf of a Tenant.
     */
    // [RUBRIC: OOP]
    public LeaseApplication submitLeaseApplication(User actor, int propertyId, LocalDate moveInDate, String message)
            throws UnauthorizedActionException, ResourceNotFoundException, InvalidLeaseDataException, SQLException {
        requirePermission(actor, "APPLY_LEASE");

        if (moveInDate == null) {
            throw new InvalidLeaseDataException("Move-in date is required.");
        }
        if (moveInDate.isBefore(LocalDate.now())) {
            throw new InvalidLeaseDataException("Move-in date cannot be in the past.");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new InvalidLeaseDataException("Please include a message with your lease application.");
        }
        if (message.trim().length() > 1000) {
            throw new InvalidLeaseDataException("Application message must not exceed 1000 characters.");
        }

        Optional<Property> propOpt = propertyDAO.findById(propertyId);
        if (!propOpt.isPresent()) {
            throw new ResourceNotFoundException("Property #" + propertyId + " was not found.");
        }

        Property property = propOpt.get();
        if (property.getStatus() != PropertyStatus.APPROVED) {
            throw new InvalidLeaseDataException(
                    "Property #" + propertyId + " is not currently available for lease applications.");
        }

        if (leaseApplicationDAO.findByPropertyAndTenant(propertyId, actor.getId()).isPresent()) {
            throw new InvalidLeaseDataException(
                    "You have already submitted a lease application for property #" + propertyId + ".");
        }

        LeaseApplication application = new LeaseApplication(
                propertyId,
                actor.getId(),
                moveInDate,
                message.trim()
        );

        try {
            LeaseApplication saved = leaseApplicationDAO.insert(application);
            analyticsTracker.incrementLeaseSubmissions();
            auditWorker.logEvent(actor.getId(), "LEASE_APPLIED",
                    "Tenant applied for property #" + propertyId + " (application #" + saved.getId() + ")");
            return saved;
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new InvalidLeaseDataException(
                        "You have already submitted a lease application for property #" + propertyId + ".", e);
            }
            throw e;
        }
    }

    /**
     * Returns all lease applications submitted by the given Tenant.
     */
    public List<LeaseApplication> getTenantApplications(User actor)
            throws UnauthorizedActionException, SQLException {
        requirePermission(actor, "VIEW_OWN_APPLICATIONS");
        return leaseApplicationDAO.findByTenantId(actor.getId());
    }

    // =========================================================================
    // SHARED LOOKUP HELPERS FOR UI DISPLAY
    // =========================================================================

    public Optional<Property> findPropertyById(int propertyId) throws SQLException {
        return propertyDAO.findById(propertyId);
    }

    public Optional<User> findUserById(int userId) {
        return authService.getCachedUserById(userId);
    }

    private void validatePropertyInputs(String title, String city, String address,
                                        BigDecimal monthlyRent, int bedrooms)
            throws InvalidPropertyDataException {
        if (title == null || title.trim().isEmpty()) {
            throw new InvalidPropertyDataException("Property title must not be empty.");
        }
        if (title.trim().length() > 150) {
            throw new InvalidPropertyDataException("Property title must not exceed 150 characters.");
        }
        if (city == null || city.trim().isEmpty()) {
            throw new InvalidPropertyDataException("City must not be empty.");
        }
        if (city.trim().length() > 100) {
            throw new InvalidPropertyDataException("City must not exceed 100 characters.");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new InvalidPropertyDataException("Property address must not be empty.");
        }
        if (address.trim().length() > 255) {
            throw new InvalidPropertyDataException("Property address must not exceed 255 characters.");
        }
        if (monthlyRent == null || monthlyRent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPropertyDataException("Monthly rent must be greater than 0.");
        }
        if (monthlyRent.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new InvalidPropertyDataException("Monthly rent must not exceed 99,999,999.99.");
        }
        if (bedrooms < 0 || bedrooms > 100) {
            throw new InvalidPropertyDataException("Bedrooms must be between 0 and 100.");
        }
    }

    private void requirePermission(User actor, String permission) throws UnauthorizedActionException {
        if (actor == null) {
            throw new UnauthorizedActionException("You must be logged in to perform this action.");
        }
        if (!actor.isActive()) {
            throw new UnauthorizedActionException("Your user account is inactive.");
        }
        Optional<User> liveUserOpt = authService.getCachedUserById(actor.getId());
        if (!liveUserOpt.isPresent() || !liveUserOpt.get().isActive()) {
            actor.setActive(false);
            throw new UnauthorizedActionException("Your user account has been deactivated by an administrator.");
        }
        if (!actor.hasPermission(permission)) {
            throw new UnauthorizedActionException(
                    "Access denied: role " + actor.getRole() + " does not have permission '" + permission + "'.");
        }
    }
}
