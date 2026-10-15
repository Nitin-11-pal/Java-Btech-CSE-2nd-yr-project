package com.realestate.dao;

import com.realestate.model.Property;
import com.realestate.model.PropertyStatus;
import com.realestate.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC DAO implementation for Property listings, including pessimistic row locking (SELECT ... FOR UPDATE).
 */
// [RUBRIC: JDBC] [RUBRIC: Collections] Implements generic DAO<Property> using PreparedStatement
public class PropertyDAO implements DAO<Property> {

    private static final String INSERT_SQL =
            "INSERT INTO properties (manager_id, title, city, address, monthly_rent, bedrooms, status, rejection_reason) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL =
            "UPDATE properties SET manager_id = ?, title = ?, city = ?, address = ?, monthly_rent = ?, " +
            "bedrooms = ?, status = ?, rejection_reason = ? WHERE id = ?";
    private static final String UPDATE_STATUS_SQL =
            "UPDATE properties SET status = ?, rejection_reason = ? WHERE id = ?";
    private static final String DELETE_SQL =
            "DELETE FROM properties WHERE id = ?";
    private static final String FIND_BY_ID_SQL =
            "SELECT id, manager_id, title, city, address, monthly_rent, bedrooms, status, rejection_reason " +
            "FROM properties WHERE id = ?";
    private static final String FIND_BY_ID_FOR_UPDATE_SQL =
            "SELECT id, manager_id, title, city, address, monthly_rent, bedrooms, status, rejection_reason " +
            "FROM properties WHERE id = ? FOR UPDATE";
    private static final String FIND_ALL_SQL =
            "SELECT id, manager_id, title, city, address, monthly_rent, bedrooms, status, rejection_reason " +
            "FROM properties ORDER BY id ASC";
    private static final String FIND_BY_MANAGER_SQL =
            "SELECT id, manager_id, title, city, address, monthly_rent, bedrooms, status, rejection_reason " +
            "FROM properties WHERE manager_id = ? ORDER BY id ASC";
    private static final String FIND_BY_STATUS_SQL =
            "SELECT id, manager_id, title, city, address, monthly_rent, bedrooms, status, rejection_reason " +
            "FROM properties WHERE status = ? ORDER BY id ASC";

    @Override
    // [RUBRIC: JDBC]
    public Property insert(Property property) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return insert(conn, property);
        }
    }

    // [RUBRIC: JDBC]
    public Property insert(Connection conn, Property property) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, property.getManagerId());
            ps.setString(2, property.getTitle());
            ps.setString(3, property.getCity());
            ps.setString(4, property.getAddress());
            ps.setBigDecimal(5, property.getMonthlyRent());
            ps.setInt(6, property.getBedrooms());
            ps.setString(7, property.getStatus().name());
            ps.setString(8, property.getRejectionReason());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    property.setId(keys.getInt(1));
                }
            }
            return property;
        }
    }

    @Override
    // [RUBRIC: JDBC]
    public boolean update(Property property) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return update(conn, property);
        }
    }

    // [RUBRIC: JDBC]
    public boolean update(Connection conn, Property property) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setInt(1, property.getManagerId());
            ps.setString(2, property.getTitle());
            ps.setString(3, property.getCity());
            ps.setString(4, property.getAddress());
            ps.setBigDecimal(5, property.getMonthlyRent());
            ps.setInt(6, property.getBedrooms());
            ps.setString(7, property.getStatus().name());
            ps.setString(8, property.getRejectionReason());
            ps.setInt(9, property.getId());
            return ps.executeUpdate() > 0;
        }
    }

    // [RUBRIC: JDBC]
    public boolean updateStatus(int propertyId, PropertyStatus status, String rejectionReason) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return updateStatus(conn, propertyId, status, rejectionReason);
        }
    }

    // [RUBRIC: JDBC] [RUBRIC: Transaction] Connection overload used inside approveLease transaction
    public boolean updateStatus(Connection conn, int propertyId, PropertyStatus status, String rejectionReason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_SQL)) {
            ps.setString(1, status.name());
            ps.setString(2, rejectionReason);
            ps.setInt(3, propertyId);
            return ps.executeUpdate() > 0;
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
    public Optional<Property> findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findById(conn, id);
        }
    }

    // [RUBRIC: JDBC]
    public Optional<Property> findById(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_SQL)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToProperty(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Locks the property row in the database using SELECT ... FOR UPDATE within an active transaction.
     *
     * @param conn active transactional Connection with autoCommit=false
     * @param id   property ID to lock
     * @return Optional containing the locked Property row
     * @throws SQLException if a database error occurs
     */
    // [RUBRIC: JDBC] [RUBRIC: Transaction] Pessimistic row lock for atomic lease approval
    public Optional<Property> findByIdForUpdate(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_FOR_UPDATE_SQL)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToProperty(rs));
                }
                return Optional.empty();
            }
        }
    }

    @Override
    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public ArrayList<Property> findAll() throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findAll(conn);
        }
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections] Returns ArrayList<Property>
    public ArrayList<Property> findAll(Connection conn) throws SQLException {
        ArrayList<Property> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToProperty(rs));
            }
        }
        return list;
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<Property> findByManagerId(int managerId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findByManagerId(conn, managerId);
        }
    }

    // [RUBRIC: JDBC]
    public List<Property> findByManagerId(Connection conn, int managerId) throws SQLException {
        List<Property> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_MANAGER_SQL)) {
            ps.setInt(1, managerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToProperty(rs));
                }
            }
        }
        return list;
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public ArrayList<Property> findByStatus(PropertyStatus status) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findByStatus(conn, status);
        }
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public ArrayList<Property> findByStatus(Connection conn, PropertyStatus status) throws SQLException {
        ArrayList<Property> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_STATUS_SQL)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToProperty(rs));
                }
            }
        }
        return list;
    }

    private Property mapRowToProperty(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int managerId = rs.getInt("manager_id");
        String title = rs.getString("title");
        String city = rs.getString("city");
        String address = rs.getString("address");
        BigDecimal monthlyRent = rs.getBigDecimal("monthly_rent");
        int bedrooms = rs.getInt("bedrooms");
        PropertyStatus status = PropertyStatus.valueOf(rs.getString("status"));
        String rejectionReason = rs.getString("rejection_reason");

        return new Property(id, managerId, title, city, address, monthlyRent, bedrooms, status, rejectionReason);
    }
}
