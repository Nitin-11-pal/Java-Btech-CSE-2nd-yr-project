# Online Real Estate Management System (Java Swing & MySQL JDBC)

A complete, beginner-readable, university-rubric-compliant desktop application built exclusively with JDK 17+, Java Swing, and MySQL Connector/J (`JDBC`). Every rubric requirement (33 marks total) is explicitly implemented, tagged with rubric comments (`// [RUBRIC: ...]`), and structured in strict architectural layers (`ui -> service -> dao -> MySQL`).

## Confirmed Final Specification

- **Dual Row Locking in `approveLease`**: Locks both the `LeaseApplication` row (`SELECT ... FOR UPDATE`) and the `Property` row (`SELECT ... FOR UPDATE`). Verifies the lease status is still `PENDING`, belongs to the locked property, and the property status is still `APPROVED` (otherwise throws `InvalidLeaseDataException`).
- **Guarded Property Deletion & Soft User Deactivation**: Deleting a property is allowed only when it has zero lease applications (otherwise throws `InvalidPropertyDataException`). Admin user removal defaults to soft deactivation (`active = false`).
- **Thread-Safe `HashMap<Integer, User>` Cache**: Guarded with `ReentrantReadWriteLock` for safe concurrent access from `SwingWorker` threads.
- **Unique Lease Application Constraint**: `lease_applications` enforces `UNIQUE(property_id, tenant_id)` and the service throws `InvalidLeaseDataException` on duplicate applications.
- **`BigDecimal` & `DECIMAL(10,2)` Financial Precision**: Used across all models, DAOs, services, and `schema.sql` for `monthly_rent` and commission `amount`.
- **Standard JDK `PBKDF2WithHmacSHA256` Password Hashing**: `PasswordUtil` uses `javax.crypto.SecretKeyFactory` (`PBKDF2WithHmacSHA256`, 120,000 iterations, 16-byte `SecureRandom` salt) and constant-time `MessageDigest.isEqual` comparison.
- **Database Initialization**: `schema.sql` begins with `CREATE DATABASE IF NOT EXISTS real_estate_db; USE real_estate_db;`.
- **Generation Sequence**:
  - **Part 1**: `schema.sql`, `db.properties`, and 6 Checked Exceptions (`com.realestate.exception.*`)
  - **Part 2**: Models (`com.realestate.model.*`), Generic `DAO<T>`, and Utilities (`Result<T>`, `PasswordUtil`, `SessionManager`)
  - **Part 3**: `DBConnection` Singleton and Specific DAOs (`UserDAO`, `PropertyDAO`, `LeaseApplicationDAO`, `CommissionDAO`, `AuditLogDAO`) with `Connection` overloads
  - **Part 4**: Workers & Services (`AnalyticsTracker`, `AuditWorker`, `AuthService`, `RealEstateService`, `SeedData`)
  - **Part 5a**: Swing Auth & Frame (`LoginPanel`, `RegisterPanel`, `MainFrame`, `RealEstateApp`)
  - **Part 5b**: Swing Role Dashboards (`AdminDashboard`, `ManagerDashboard`, `TenantDashboard`), `README.md`, Demo Credentials, and Rubric Mapping Table.
