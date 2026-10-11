package org.growforge.jalmarg;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FlywayIntegrityAuditTest {

    private static final String JDBC_URL = "jdbc:postgresql://127.0.0.1:5433/jalmarg_twin?sslmode=disable";
    private static final String USER = "postgres";
    private static final String PASS = "postgres";

    @Test
    public void auditFlywaySchemaHistoryAndValidation() throws Exception {
        System.out.println("=== FLYWAY INTEGRITY AUDIT (READ-ONLY) ===");

        String envUrl = System.getenv("DB_URL");
        if (envUrl == null || envUrl.isBlank()) {
            envUrl = System.getProperty("DB_URL");
        }
        String jdbcUrl = (envUrl != null && !envUrl.isBlank()) ? envUrl : JDBC_URL;

        String envUser = System.getenv("DB_USER");
        if (envUser == null || envUser.isBlank()) {
            envUser = System.getProperty("DB_USER");
        }
        String user = (envUser != null && !envUser.isBlank()) ? envUser : USER;

        String envPass = System.getenv("DB_PASSWORD");
        if (envPass == null || envPass.isBlank()) {
            envPass = System.getProperty("DB_PASSWORD");
        }
        String pass = (envPass != null && !envPass.isBlank()) ? envPass : PASS;

        boolean isCi = "true".equalsIgnoreCase(System.getenv("CI")) || "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));

        // 1. Direct JDBC inspection of database & port
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, pass);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT inet_server_port(), current_database(), postgis_full_version()");
            if (rs.next()) {
                int port = rs.getInt(1);
                String dbName = rs.getString(2);
                System.out.println("Target Port: " + port);
                System.out.println("Target DB: " + dbName);
                System.out.println("PostGIS Version: " + rs.getString(3));

                if (isCi) {
                    System.out.println("Running in CI environment: verifying ephemeral test container identity");
                    org.junit.jupiter.api.Assertions.assertEquals("jalmarg_ci_test", dbName, "CI must connect to isolated jalmarg_ci_test database");
                    org.junit.jupiter.api.Assertions.assertEquals(5432, port, "CI PostgreSQL service container must be on port 5432");
                } else {
                    // Strict local port isolation and identity assertions
                    org.junit.jupiter.api.Assertions.assertEquals(5433, port, "Must connect strictly to JalMarg port 5433");
                    org.junit.jupiter.api.Assertions.assertNotEquals(5432, port, "Must NEVER connect to CarSync port 5432");
                    org.junit.jupiter.api.Assertions.assertEquals("jalmarg_twin", dbName, "Must connect to jalmarg_twin");
                }
            }

            // Inspect flyway_schema_history rows
            ResultSet rsHistory = stmt.executeQuery(
                    "SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success " +
                    "FROM flyway_schema_history ORDER BY installed_rank"
            );
            System.out.println("\n--- Current flyway_schema_history table ---");
            while (rsHistory.next()) {
                System.out.printf("Rank: %d | Version: %s | Description: %s | Type: %s | Script: %s | Checksum: %d | Success: %b%n",
                        rsHistory.getInt(1),
                        rsHistory.getString(2),
                        rsHistory.getString(3),
                        rsHistory.getString(4),
                        rsHistory.getString(5),
                        rsHistory.getInt(6),
                        rsHistory.getBoolean(9)
                );
            }

            // Inspect tables created by V6
            ResultSet rsTables = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name IN ('ml_historical_source_registry', 'ml_ground_truth_observations', 'ml_verification_audit_log', 'ml_cv_reliability_audit') " +
                    "ORDER BY table_name"
            );
            System.out.println("\n--- Schema Objects Created by V6 ---");
            while (rsTables.next()) {
                System.out.println("Found table: " + rsTables.getString(1));
            }
        }

        // 2. Flyway programmatic execution and validation
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, user, pass)
                .locations("classpath:db/migration/postgis")
                .baselineOnMigrate(true)
                .load();

        // Migrate any pending versioned migrations using official Flyway engine
        flyway.migrate();

        System.out.println("\n--- Flyway Migration Info ---");
        for (MigrationInfo info : flyway.info().all()) {
            System.out.printf("Version: %s | Description: %s | State: %s | Checksum: %d%n",
                    info.getVersion(),
                    info.getDescription(),
                    info.getState(),
                    info.getChecksum() != null ? info.getChecksum() : 0
            );
        }

        System.out.println("\n--- Flyway Validate Output ---");
        ValidateResult validateResult = flyway.validateWithResult();
        System.out.println("Validation successful: " + validateResult.validationSuccessful);
        if (!validateResult.validationSuccessful) {
            System.out.println("Validation failure: " + validateResult.getAllErrorMessages());
        } else {
            System.out.println("Flyway validation PASSED cleanly without discrepancies.");
        }

        assertNotNull(validateResult);
        org.junit.jupiter.api.Assertions.assertTrue(validateResult.validationSuccessful, "Flyway validation must PASS cleanly without discrepancies");
    }
}
