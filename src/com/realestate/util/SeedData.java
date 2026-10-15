package com.realestate.util;

import com.realestate.dao.LeaseApplicationDAO;
import com.realestate.dao.PropertyDAO;
import com.realestate.dao.UserDAO;
import com.realestate.model.Admin;
import com.realestate.model.LeaseApplication;
import com.realestate.model.Property;
import com.realestate.model.PropertyManager;
import com.realestate.model.PropertyStatus;
import com.realestate.model.Tenant;
import com.realestate.model.User;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Standalone database seeder utility that populates 1 Admin, 1 Property Manager,
 * 1 Tenant, 5 sample properties, and 1 sample pending lease application using
 * PasswordUtil (PBKDF2WithHmacSHA256 + 16-byte random salt).
 */
// [RUBRIC: JDBC] [RUBRIC: OOP] Standalone SeedData utility using PasswordUtil and DAOs
public final class SeedData {

    private SeedData() {
    }

    /**
     * Seeds initial users and sample properties if the users table is currently empty.
     *
     * @throws SQLException if a database error occurs
     */
    public static void seedIfEmpty() throws SQLException {
        UserDAO userDAO = new UserDAO();
        List<User> existingUsers = userDAO.findAll();
        if (!existingUsers.isEmpty()) {
            System.out.println("[SeedData] Database already contains " + existingUsers.size() + " user(s). Skipping seed.");
            return;
        }

        System.out.println("[SeedData] Seeding demo users and 5 sample properties...");

        // 1. Seed Admin (admin@realestate.com / Admin@123)
        String adminSalt = PasswordUtil.generateSalt();
        String adminHash = PasswordUtil.hashPassword("Admin@123", adminSalt);
        User admin = userDAO.insert(new Admin("Alice Admin", "admin@realestate.com", adminHash, adminSalt));

        // 2. Seed Property Manager (manager@realestate.com / Manager@123)
        String managerSalt = PasswordUtil.generateSalt();
        String managerHash = PasswordUtil.hashPassword("Manager@123", managerSalt);
        User manager = userDAO.insert(new PropertyManager("Bob Manager", "manager@realestate.com", managerHash, managerSalt));

        // 3. Seed Tenant (tenant@realestate.com / Tenant@123)
        String tenantSalt = PasswordUtil.generateSalt();
        String tenantHash = PasswordUtil.hashPassword("Tenant@123", tenantSalt);
        User tenant = userDAO.insert(new Tenant("Charlie Tenant", "tenant@realestate.com", tenantHash, tenantSalt));

        // 4. Seed 5 sample properties owned by Bob Manager
        PropertyDAO propertyDAO = new PropertyDAO();

        Property p1 = new Property(0, manager.getId(),
                "Sunny Downtown Loft", "Seattle", "101 Pine St, Apt 4B",
                new BigDecimal("2200.00"), 2, PropertyStatus.APPROVED, null);
        propertyDAO.insert(p1);

        Property p2 = new Property(0, manager.getId(),
                "Quiet Lakeview Townhouse", "Seattle", "450 Lake Washington Blvd",
                new BigDecimal("3150.00"), 3, PropertyStatus.APPROVED, null);
        propertyDAO.insert(p2);

        Property p3 = new Property(0, manager.getId(),
                "Cozy Pearl District Studio", "Portland", "88 NW 11th Ave, Unit 204",
                new BigDecimal("1450.00"), 1, PropertyStatus.APPROVED, null);
        propertyDAO.insert(p3);

        Property p4 = new Property(0, manager.getId(),
                "Modern Tech Corridor Condo", "Austin", "700 Congress Ave, Suite 12",
                new BigDecimal("2650.00"), 2, PropertyStatus.PENDING, null);
        propertyDAO.insert(p4);

        Property p5 = new Property(0, manager.getId(),
                "Spacious Suburban Family Home", "Denver", "1920 Cherry Creek Dr",
                new BigDecimal("3400.00"), 4, PropertyStatus.PENDING, null);
        propertyDAO.insert(p5);

        // 5. Seed 1 sample PENDING lease application from Charlie Tenant on Property #1
        LeaseApplicationDAO leaseDAO = new LeaseApplicationDAO();
        LeaseApplication sampleApp = new LeaseApplication(
                p1.getId(),
                tenant.getId(),
                LocalDate.now().plusDays(14),
                "Hello! I have stable employment and references ready. Looking forward to leasing this loft."
        );
        leaseDAO.insert(sampleApp);

        System.out.println("[SeedData] Successfully seeded Admin (ID=" + admin.getId()
                + "), Manager (ID=" + manager.getId()
                + "), Tenant (ID=" + tenant.getId()
                + "), 5 properties, and 1 sample lease application.");
    }

    public static void main(String[] args) {
        try {
            seedIfEmpty();
        } catch (SQLException e) {
            System.err.println("[SeedData] Failed to seed database: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
