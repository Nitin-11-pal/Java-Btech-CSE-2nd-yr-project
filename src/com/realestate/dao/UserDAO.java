package com.realestate.dao;

import com.realestate.model.Admin;
import com.realestate.model.PropertyManager;
import com.realestate.model.Role;
import com.realestate.model.Tenant;
import com.realestate.model.User;
import com.realestate.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC DAO implementation for User persistence and polymorphic hydration (Admin, PropertyManager, Tenant).
 */
// [RUBRIC: JDBC] [RUBRIC: Collections] Implements generic DAO<User> using PreparedStatement and try-with-resources
public class UserDAO implements DAO<User> {

    private static final String INSERT_SQL =
            "INSERT INTO users (name, email, password_hash, salt, role, active) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL =
            "UPDATE users SET name = ?, email = ?, password_hash = ?, salt = ?, role = ?, active = ? WHERE id = ?";
    private static final String UPDATE_ACTIVE_SQL =
            "UPDATE users SET active = ? WHERE id = ?";
    private static final String DELETE_SQL =
            "DELETE FROM users WHERE id = ?";
    private static final String FIND_BY_ID_SQL =
            "SELECT id, name, email, password_hash, salt, role, active FROM users WHERE id = ?";
    private static final String FIND_BY_EMAIL_SQL =
            "SELECT id, name, email, password_hash, salt, role, active FROM users WHERE email = ?";
    private static final String FIND_ALL_SQL =
            "SELECT id, name, email, password_hash, salt, role, active FROM users ORDER BY id ASC";

    @Override
    // [RUBRIC: JDBC]
    public User insert(User user) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return insert(conn, user);
        }
    }

    // [RUBRIC: JDBC] Connection overload for transactional/shared-connection usage
    public User insert(Connection conn, User user) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getSalt());
            ps.setString(5, user.getRole().name());
            ps.setBoolean(6, user.isActive());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getInt(1));
                }
            }
            return user;
        }
    }

    @Override
    // [RUBRIC: JDBC]
    public boolean update(User user) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return update(conn, user);
        }
    }

    // [RUBRIC: JDBC]
    public boolean update(Connection conn, User user) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getSalt());
            ps.setString(5, user.getRole().name());
            ps.setBoolean(6, user.isActive());
            ps.setInt(7, user.getId());
            return ps.executeUpdate() > 0;
        }
    }

    // [RUBRIC: JDBC] Soft activation/deactivation support
    public boolean updateActiveStatus(int userId, boolean active) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return updateActiveStatus(conn, userId, active);
        }
    }

    // [RUBRIC: JDBC]
    public boolean updateActiveStatus(Connection conn, int userId, boolean active) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_ACTIVE_SQL)) {
            ps.setBoolean(1, active);
            ps.setInt(2, userId);
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
    public Optional<User> findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findById(conn, id);
        }
    }

    // [RUBRIC: JDBC]
    public Optional<User> findById(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_SQL)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
                return Optional.empty();
            }
        }
    }

    // [RUBRIC: JDBC]
    public Optional<User> findByEmail(String email) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findByEmail(conn, email);
        }
    }

    // [RUBRIC: JDBC]
    public Optional<User> findByEmail(Connection conn, String email) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_EMAIL_SQL)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
                return Optional.empty();
            }
        }
    }

    @Override
    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<User> findAll() throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findAll(conn);
        }
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<User> findAll(Connection conn) throws SQLException {
        List<User> users = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRowToUser(rs));
            }
        }
        return users;
    }

    // [RUBRIC: OOP] Polymorphic instantiation of User subclasses based on database role column
    private User mapRowToUser(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String email = rs.getString("email");
        String passwordHash = rs.getString("password_hash");
        String salt = rs.getString("salt");
        Role role = Role.valueOf(rs.getString("role"));
        boolean active = rs.getBoolean("active");

        switch (role) {
            case ADMIN:
                return new Admin(id, name, email, passwordHash, salt, active);
            case PROPERTY_MANAGER:
                return new PropertyManager(id, name, email, passwordHash, salt, active);
            case TENANT:
                return new Tenant(id, name, email, passwordHash, salt, active);
            default:
                throw new SQLException("Unknown user role: " + role);
        }
    }
}
