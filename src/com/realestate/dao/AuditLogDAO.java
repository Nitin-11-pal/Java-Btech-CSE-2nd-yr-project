package com.realestate.dao;

import com.realestate.model.AuditLog;
import com.realestate.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC DAO implementation for AuditLog records consumed and persisted by AuditWorker.
 */
// [RUBRIC: JDBC] [RUBRIC: Collections] Implements generic DAO<AuditLog>
public class AuditLogDAO implements DAO<AuditLog> {

    private static final String INSERT_SQL =
            "INSERT INTO audit_log (user_id, action, details) VALUES (?, ?, ?)";
    private static final String UPDATE_SQL =
            "UPDATE audit_log SET user_id = ?, action = ?, details = ? WHERE id = ?";
    private static final String DELETE_SQL =
            "DELETE FROM audit_log WHERE id = ?";
    private static final String FIND_BY_ID_SQL =
            "SELECT id, user_id, action, details, created_at FROM audit_log WHERE id = ?";
    private static final String FIND_ALL_SQL =
            "SELECT id, user_id, action, details, created_at FROM audit_log ORDER BY id DESC";

    @Override
    // [RUBRIC: JDBC]
    public AuditLog insert(AuditLog log) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return insert(conn, log);
        }
    }

    // [RUBRIC: JDBC]
    public AuditLog insert(Connection conn, AuditLog log) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            if (log.getUserId() != null && log.getUserId() > 0) {
                ps.setInt(1, log.getUserId());
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, log.getAction());
            ps.setString(3, log.getDetails());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    log.setId(keys.getInt(1));
                }
            }
            return log;
        }
    }

    @Override
    // [RUBRIC: JDBC]
    public boolean update(AuditLog log) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return update(conn, log);
        }
    }

    // [RUBRIC: JDBC]
    public boolean update(Connection conn, AuditLog log) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            if (log.getUserId() != null && log.getUserId() > 0) {
                ps.setInt(1, log.getUserId());
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, log.getAction());
            ps.setString(3, log.getDetails());
            ps.setInt(4, log.getId());
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
    public Optional<AuditLog> findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findById(conn, id);
        }
    }

    // [RUBRIC: JDBC]
    public Optional<AuditLog> findById(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_SQL)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToAuditLog(rs));
                }
                return Optional.empty();
            }
        }
    }

    @Override
    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<AuditLog> findAll() throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findAll(conn);
        }
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<AuditLog> findAll(Connection conn) throws SQLException {
        List<AuditLog> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToAuditLog(rs));
            }
        }
        return list;
    }

    private AuditLog mapRowToAuditLog(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int rawUserId = rs.getInt("user_id");
        Integer userId = rs.wasNull() ? null : rawUserId;
        String action = rs.getString("action");
        String details = rs.getString("details");
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();

        return new AuditLog(id, userId, action, details, createdAt);
    }
}
