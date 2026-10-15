package com.realestate.dao;

import com.realestate.model.Commission;
import com.realestate.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC DAO implementation for manager Commission records.
 */
// [RUBRIC: JDBC] [RUBRIC: Collections] Implements generic DAO<Commission>
public class CommissionDAO implements DAO<Commission> {

    private static final String INSERT_SQL =
            "INSERT INTO commissions (manager_id, lease_id, amount) VALUES (?, ?, ?)";
    private static final String UPDATE_SQL =
            "UPDATE commissions SET manager_id = ?, lease_id = ?, amount = ? WHERE id = ?";
    private static final String DELETE_SQL =
            "DELETE FROM commissions WHERE id = ?";
    private static final String FIND_BY_ID_SQL =
            "SELECT id, manager_id, lease_id, amount, created_at FROM commissions WHERE id = ?";
    private static final String FIND_BY_MANAGER_SQL =
            "SELECT id, manager_id, lease_id, amount, created_at FROM commissions WHERE manager_id = ? ORDER BY id DESC";
    private static final String FIND_ALL_SQL =
            "SELECT id, manager_id, lease_id, amount, created_at FROM commissions ORDER BY id DESC";

    @Override
    // [RUBRIC: JDBC]
    public Commission insert(Commission commission) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return insert(conn, commission);
        }
    }

    // [RUBRIC: JDBC] [RUBRIC: Transaction] Connection overload used inside approveLease transaction
    public Commission insert(Connection conn, Commission commission) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, commission.getManagerId());
            ps.setInt(2, commission.getLeaseId());
            ps.setBigDecimal(3, commission.getAmount());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    commission.setId(keys.getInt(1));
                }
            }
            return commission;
        }
    }

    @Override
    // [RUBRIC: JDBC]
    public boolean update(Commission commission) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return update(conn, commission);
        }
    }

    // [RUBRIC: JDBC]
    public boolean update(Connection conn, Commission commission) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setInt(1, commission.getManagerId());
            ps.setInt(2, commission.getLeaseId());
            ps.setBigDecimal(3, commission.getAmount());
            ps.setInt(4, commission.getId());
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
    public Optional<Commission> findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findById(conn, id);
        }
    }

    // [RUBRIC: JDBC]
    public Optional<Commission> findById(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_SQL)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToCommission(rs));
                }
                return Optional.empty();
            }
        }
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<Commission> findByManagerId(int managerId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findByManagerId(conn, managerId);
        }
    }

    // [RUBRIC: JDBC]
    public List<Commission> findByManagerId(Connection conn, int managerId) throws SQLException {
        List<Commission> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_MANAGER_SQL)) {
            ps.setInt(1, managerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToCommission(rs));
                }
            }
        }
        return list;
    }

    @Override
    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<Commission> findAll() throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return findAll(conn);
        }
    }

    // [RUBRIC: JDBC] [RUBRIC: Collections]
    public List<Commission> findAll(Connection conn) throws SQLException {
        List<Commission> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToCommission(rs));
            }
        }
        return list;
    }

    private Commission mapRowToCommission(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int managerId = rs.getInt("manager_id");
        int leaseId = rs.getInt("lease_id");
        BigDecimal amount = rs.getBigDecimal("amount");
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();

        return new Commission(id, managerId, leaseId, amount, createdAt);
    }
}
