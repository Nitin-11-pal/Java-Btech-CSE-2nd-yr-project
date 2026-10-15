-- [RUBRIC: JDBC] [RUBRIC: Transaction]
-- MySQL 8 Schema for Online Real Estate Management System

CREATE DATABASE IF NOT EXISTS real_estate_db;
USE real_estate_db;

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS commissions;
DROP TABLE IF EXISTS lease_applications;
DROP TABLE IF EXISTS properties;
DROP TABLE IF EXISTS users;

-- 1. Users table
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'PROPERTY_MANAGER', 'TENANT') NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX idx_users_role (role),
    INDEX idx_users_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Properties table
CREATE TABLE properties (
    id INT AUTO_INCREMENT PRIMARY KEY,
    manager_id INT NOT NULL,
    title VARCHAR(150) NOT NULL,
    city VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    monthly_rent DECIMAL(10, 2) NOT NULL,
    bedrooms INT NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED', 'RENTED') NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500) NULL,
    CONSTRAINT fk_properties_manager
        FOREIGN KEY (manager_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_properties_manager (manager_id),
    INDEX idx_properties_status (status),
    INDEX idx_properties_city (city),
    INDEX idx_properties_rent (monthly_rent)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Lease Applications table
CREATE TABLE lease_applications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    property_id INT NOT NULL,
    tenant_id INT NOT NULL,
    move_in_date DATE NOT NULL,
    message VARCHAR(1000) NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    CONSTRAINT uq_lease_property_tenant UNIQUE (property_id, tenant_id),
    CONSTRAINT fk_lease_property
        FOREIGN KEY (property_id) REFERENCES properties(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_lease_tenant
        FOREIGN KEY (tenant_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_lease_property (property_id),
    INDEX idx_lease_tenant (tenant_id),
    INDEX idx_lease_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Commissions table
CREATE TABLE commissions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    manager_id INT NOT NULL,
    lease_id INT NOT NULL UNIQUE,
    amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_commissions_manager
        FOREIGN KEY (manager_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_commissions_lease
        FOREIGN KEY (lease_id) REFERENCES lease_applications(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_commissions_manager (manager_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Audit Log table
CREATE TABLE audit_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NULL,
    action VARCHAR(100) NOT NULL,
    details VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
