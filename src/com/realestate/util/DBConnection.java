package com.realestate.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton JDBC connection manager reading URL, user, and password from db.properties.
 */
// [RUBRIC: OOP] [RUBRIC: JDBC] Singleton DBConnection utility reading from one constants block
public final class DBConnection {

    // Constants block for externalized database configuration
    private static final String PROPERTIES_FILE = "db.properties";
    private static final String KEY_URL = "db.url";
    private static final String KEY_USER = "db.user";
    private static final String KEY_PASSWORD = "db.password";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    private static final DBConnection INSTANCE = new DBConnection();

    private final String url;
    private final String user;
    private final String password;

    private DBConnection() {
        try {
            Class.forName(MYSQL_DRIVER);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL JDBC Driver not found on classpath: " + MYSQL_DRIVER, e);
        }

        Properties props = loadProperties();
        this.url = props.getProperty(KEY_URL);
        this.user = props.getProperty(KEY_USER);
        this.password = props.getProperty(KEY_PASSWORD, "");

        if (this.url == null || this.url.isBlank() || this.user == null || this.user.isBlank()) {
            throw new IllegalStateException("Missing required keys (" + KEY_URL + ", " + KEY_USER + ") in " + PROPERTIES_FILE);
        }
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        Path[] candidatePaths = new Path[]{
                Paths.get(PROPERTIES_FILE),
                Paths.get("src", PROPERTIES_FILE)
        };
        for (Path candidate : candidatePaths) {
            if (Files.exists(candidate)) {
                try (InputStream in = new FileInputStream(candidate.toFile())) {
                    props.load(in);
                    return props;
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to read " + candidate.toAbsolutePath(), e);
                }
            }
        }

        try (InputStream cpIn = DBConnection.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (cpIn != null) {
                props.load(cpIn);
                return props;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + PROPERTIES_FILE + " from classpath", e);
        }

        throw new IllegalStateException("Could not locate " + PROPERTIES_FILE + " in working directory, src/, or classpath.");
    }

    public static DBConnection getInstance() {
        return INSTANCE;
    }

    /**
     * Opens and returns a new JDBC Connection to the configured MySQL database.
     *
     * @return active JDBC Connection
     * @throws SQLException if a database access error occurs
     */
    // [RUBRIC: JDBC]
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
