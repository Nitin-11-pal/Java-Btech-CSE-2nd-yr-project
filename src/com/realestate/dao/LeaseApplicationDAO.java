package com.realestate.dao;

import com.realestate.model.LeaseApplication;
import com.realestate.model.LeaseStatus;
import com.realestate.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC DAO implementation for LeaseApplication records, including row locking and batch rejection.
 */
// [RUBRIC: JDBC] [RUBRIC: Collections] Implements generic DAO<LeaseApplication>
public class LeaseApplicationDAO implements DAO<LeaseApplication> {

    private static final String INSERT_SQL =
            "INSERT INTO lease_applications (property_id, tenant_id, move_in_date, message, status) " +
            "VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL =
            "UPDATE lease_applications SET property_id = ?, tenant_id = ?, move_in_date = ?, " +
            "message = ?, status = ? WHERE id = ?";
    private static final String UPDATE_STATUS_SQL =
            "UPDATE lease_applications SET status = ? WHERE id = ?";
    private static final String REJECT_OTHER_PENDING_SQL =
            "UPDATE lease_applications SET status = 'REJECTED' " +
            "WHERE property_id = ? AND id <> ? AND status = 'PENDING'";
    private static final String DELETE_SQL =
            "DELETE FROM lease_applications WHERE id = ?";
    private static final String FIND_BY_ID_SQL =
            "SELECT id, property_id, tenant_id, move_in_date, message, status " +
            "FROM lease_applications WHERE id = ?";
    private static final String FIND_BY_ID_FOR_UPDATE_SQL =
            "SELECT id, property_id, tenant_id, move_in_date, message, status " +
            "FROM lease_applications WHERE id = ? FOR UPDATE";
    private static final String FIND_BY_PROPERTY_AND_TENANT_SQL =
            "SELECT id, property_id, tenant_id, move_in_date, message, status " +
            "FROM lease_applications WHERE property_id = ? AND tenant_id = ?";
    private static final String COUNT_BY_PROPERTY_SQL =
            "SELECT COUNT(*) FROM lease_applications WHERE property_id = ?";
    private static final String FIND_BY_TENANT_SQL =
            "SELECT id, property_id, tenant_id, move_in_date, message, status " +
            "FROM lease_applications WHERE tenant_id = ? ORDER BY id DESC";
    private static final String FIND_BY_PROPERTY_SQL =
            "SELECT id, property_id, tenant_id, move_in_date, message, status " +
            "FROM lease_applications WHERE property_id = ? ORDER BY id DESC";
    private static final String FIND_BY_MANAGER_SQL =
            "SELECT la.id, la.property_id, la.tenant_id, la.move_in_date, la.message, la.status " +
            "FROM lease_applications la " +
            "INNER JOIN properties p ON la.property_id = p.id " +
            "WHERE p.manager_id = ? ORDER BY la.id DESC";
    private static final String FIND_ALL_SQL =
            "SELECT id, property_id, tenant_id, move_in_date, message, status " +
            "FROM lease_applications ORDER BY id DESC";

    @Override
    // [RUBRIC: JDBC]
    public LeaseApplication insert(LeaseApplication application) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return insert(conn, application);
        }
    }

    // [RUBRIC: JDBC]
    public LeaseApplication insert(Connection conn, LeaseApplication application) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, application.getPropertyId());
            ps.setInt(2, application.getTenantId());
            ps.setDate(3, Date.valueOf(application.getMoveInDate()));
            ps.setString(4, application.getMessage());
            ps.setString(5, application.getStatus().name());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    application.setId(keys.getInt(1));
                }
            }
            return application;
        }
    }

    @Override
    // [RUBRIC: JDBC]
    public boolean update(LeaseApplication application) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return update(conn, application);
        }
    }

    // [RUBRIC: JDBC]
    public boolean update(Connection conn, LeaseApplication application) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setInt(1, application.getPropertyId());
            ps.setInt(2, application.getTenantId());
            ps.setDate(3, Date.valueOf(application.getMoveInDate()));
            ps.setString(4, application.getMessage());
            ps.setString(5, application.getStatus().name());
            ps.setInt(6, application.getId());
            return ps.executeUpdate() > 0;
        }
    }

    // [RUBRIC: JDBC]
    public boolean updateStatus(int applicationId, LeaseStatus status) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return updateStatus(conn, applicationId, status);
        }
    }

    // [RUBRIC: JDBC] [RUBRIC: Transaction] Connection overload for transactional lease approval
    public boolean updateStatus(Connection conn, int applicationId, LeaseStatus status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_SQL)) {
            ps.setString(1, status.name());
            ps.setInt(2, applicationId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Rejects all other PENDING applications on the same property within an active transaction.
     *
     * @param conn                  active transactional Connection
     * @param propertyId            target property ID
     * @param approvedApplicationId the winning application ID to exclude from rejection
     * @return number of competing applications rejected
     * @throws SQLException if a database error occurs
     */
    // [RUBRIC: JDBC] [RUBRIC: Transaction]
    public int rejectOtherPendingByProperty(Connection conn, int propertyId, int approvedApplicationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(REJECT_OTHER_PENDING_SQL)) {
            ps.setInt(1, propertyId);
            ps.setInt(2, approvedApplicationId);
            return ps.executeUpdate();
        }
    }

    @Override
    // [RUBRIC: JDBC]
    public boolean delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return delete(conn, id);
        }
    }

    // [RUBRIC: JDBC]
    public boolean delete(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    // [RUBRIC: JDBC]
    public Optional<LeaseApplication> findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findById(conn, id);
        }
    }

    // [RUBRIC: JDBC]
    public Optional<LeaseApplication> findById(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_SQL)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToLeaseApplication(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Locks the lease application row using SELECT ... FOR UPDATE within an active transaction.
     *
     * @param conn active transactional Connection with autoCommit=false
     * @param id   lease application ID to lock
     * @return Optional containing the locked LeaseApplication
     * @throws SQLException if a database error occurs
     */
    // [RUBRIC: JDBC] [RUBRIC: Transaction] Pessimistic lock on LeaseApplication row
    public Optional<LeaseApplication> findByIdForUpdate(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_FOR_UPDATE_SQL)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToLeaseApplication(rs));
                }
                return Optional.empty();
            }
        }
    }

    // [RUBRIC: JDBC] Checks if a tenant already applied for a specific property
    public Optional<LeaseApplication> findByPropertyAndTenant(int propertyId, int tenantId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findByPropertyAndTenant(conn, propertyId, tenantId);
        }
    }

    // [RUBRIC: JDBC]
    public Optional<LeaseApplication> findByPropertyAndTenant(Connection conn, int propertyId, int tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_PROPERTY_AND_TENANT_SQL)) {
            ps.setInt(1, propertyId);
            ps.setInt(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToLeaseApplication(rs));
                }
                return Optional.empty();
            }
        }
    }

    // [RUBRIC: JDBC] Used to guard property deletion when lease applications exist
    public int countByPropertyId(int propertyId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return countByPropertyId(conn, propertyId);
        }
    }

    // [RUBRIC: JDBC]
    public int countByPropertyId(Connection conn, int propertyId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(COUNT_BY_PROPERTY_SQL)) {
            ps.setInt(1, propertyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<LeaseApplication> findByTenantId(int tenantId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findByTenantId(conn, tenantId);
        }
    }

    // [RUBRIC: JDBC]
    public List<LeaseApplication> findByTenantId(Connection conn, int tenantId) throws SQLException {
        List<LeaseApplication> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_TENANT_SQL)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToLeaseApplication(rs));
                }
            }
        }
        return list;
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<LeaseApplication> findByPropertyId(int propertyId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findByPropertyId(conn, propertyId);
        }
    }

    // [RUBRIC: JDBC]
    public List<LeaseApplication> findByPropertyId(Connection conn, int propertyId) throws SQLException {
        List<LeaseApplication> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_PROPERTY_SQL)) {
            ps.setInt(1, propertyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToLeaseApplication(rs));
                }
            }
        }
        return list;
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<LeaseApplication> findByManagerId(int managerId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findByManagerId(conn, managerId);
        }
    }

    // [RUBRIC: JDBC]
    public List<LeaseApplication> findByManagerId(Connection conn, int managerId) throws SQLException {
        List<LeaseApplication> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_MANAGER_SQL)) {
            ps.setInt(1, managerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToLeaseApplication(rs));
                }
            }
        }
        return list;
    }

    @Override
    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<LeaseApplication> findAll() throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findAll(conn);
        }
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<LeaseApplication> findAll(Connection conn) throws SQLException {
        List<LeaseApplication> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToLeaseApplication(rs));
            }
        }
        return list;
    }

    private LeaseApplication mapRowToLeaseApplication(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int propertyId = rs.getInt("property_id");
        int tenantId = rs.getInt("tenant_id");
        Date moveInSqlDate = rs.getDate("move_in_date");
        LocalDate moveInDate = moveInSqlDate != null ? moveInSqlDate.toLocalDate() : null;
        String message = rs.getString("message");
        LeaseStatus status = LeaseStatus.valueOf(rs.getString("status"));

        return new LeaseApplication(id, propertyId, tenantId, moveInDate, message, status);
    }
}
