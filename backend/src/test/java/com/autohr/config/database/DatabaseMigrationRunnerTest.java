package com.autohr.config.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationRunnerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void createsBusinessKeyIndexesAndRejectsHistoricalDuplicates() throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("migration.db").toString().replace('\\', '/');
        DataSource dataSource = new DriverManagerDataSource(url);
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(
                dataSource,
                new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false),
                new AppMigrationProperties());

        runner.run();

        try (Connection connection = dataSource.getConnection()) {
            assertUniqueIndex(connection, "hr_employee", "uq_hr_employee_source_candidate_id");
            assertUniqueIndex(connection, "recruitment_candidate", "uq_recruitment_candidate_job_interviewee");
            assertUniqueIndex(connection, "interview_process", "uq_interview_process_candidate_id");

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP INDEX uq_recruitment_candidate_job_interviewee");
                statement.executeUpdate("INSERT INTO recruitment_job "
                        + "(job_code, job_title, department_name, headcount, requirements, responsibilities, publish_date, status) "
                        + "VALUES ('JOB-1', 'Engineer', 'Technology', 1, 'Requirements', 'Responsibilities', '2026-08-14', 1)");
                statement.executeUpdate("INSERT INTO recruitment_candidate "
                        + "(job_id, full_name, mobile_phone, major, application_status, interview_stage_status, interviewee_user_id) "
                        + "VALUES (1, 'First', '13800138001', 'Computer Science', 'SUBMITTED', 'Review', 99)");
                statement.executeUpdate("INSERT INTO recruitment_candidate "
                        + "(job_id, full_name, mobile_phone, major, application_status, interview_stage_status, interviewee_user_id) "
                        + "VALUES (1, 'Second', '13800138002', 'Computer Science', 'SUBMITTED', 'Review', 99)");
            }
        }

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);
        assertTrue(error.getMessage().contains("candidate applications"));
    }

    private void assertUniqueIndex(Connection connection, String table, String expectedName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery("PRAGMA index_list('" + table + "')")) {
            boolean found = false;
            while (indexes.next()) {
                if (expectedName.equalsIgnoreCase(indexes.getString("name"))) {
                    assertEquals(1, indexes.getInt("unique"));
                    found = true;
                }
            }
            assertTrue(found, "Missing unique index " + expectedName);
        }
    }
}
