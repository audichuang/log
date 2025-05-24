package cub.ret.tru.batch.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 資料庫測試控制器
 * 直接測試資料庫連接和插入功能
 */
@Slf4j
@RestController
@RequestMapping("/api/db-test")
public class DatabaseTestController {

    private static final String DB_URL = "jdbc:postgresql://192.168.31.247:5444/postgres?useUnicode=true&characterEncoding=utf8&useSSL=false&currentSchema=public";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "VZq9rWbC3oJYFYdDrjT6edewVHQEKNCBWPDnyqxKyzMTE3CoozBrWnYsi6KkpwKujcFKDytQCrxhTbcxsAB2vswcVgQc9ieYvtpP";

    private static final String INSERT_SQL = """
            INSERT INTO batch_log (execution_id, job_name, step_name, log_level,
                                  message, logger_name, thread_name, exception_stack,
                                  log_time, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * 測試資料庫連接
     */
    @GetMapping("/connection")
    public String testConnection() {
        try (Connection connection = getConnection()) {
            return "資料庫連接成功！連接資訊: " + connection.getMetaData().getURL();
        } catch (SQLException e) {
            return "資料庫連接失敗: " + e.getMessage();
        }
    }

    /**
     * 直接插入測試資料
     */
    @PostMapping("/insert-test")
    public String insertTestData() {
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {

            String executionId = "DIRECT_TEST_" + System.currentTimeMillis();
            String jobName = "DIRECT_TEST_JOB";
            String stepName = "DIRECT_TEST_STEP";
            String logLevel = "INFO";
            String message = "直接資料庫插入測試";
            String loggerName = "DatabaseTestController";
            String threadName = Thread.currentThread().getName();
            String exceptionStack = null;

            LocalDateTime now = LocalDateTime.now();

            statement.setString(1, executionId);
            statement.setString(2, jobName);
            statement.setString(3, stepName);
            statement.setString(4, logLevel);
            statement.setString(5, message);
            statement.setString(6, loggerName);
            statement.setString(7, threadName);
            statement.setString(8, exceptionStack);
            statement.setTimestamp(9, Timestamp.valueOf(now));
            statement.setTimestamp(10, Timestamp.valueOf(now));

            int rowsAffected = statement.executeUpdate();

            return "成功插入資料！執行代號: " + executionId + ", 影響行數: " + rowsAffected;

        } catch (SQLException e) {
            return "插入資料失敗: " + e.getMessage();
        }
    }

    /**
     * 查詢測試資料
     */
    @GetMapping("/query-test")
    public String queryTestData() {
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(*) as total FROM batch_log WHERE execution_id LIKE 'DIRECT_TEST_%'")) {

            var resultSet = statement.executeQuery();
            if (resultSet.next()) {
                int count = resultSet.getInt("total");
                return "找到 " + count + " 條直接測試資料";
            }
            return "查詢失敗";

        } catch (SQLException e) {
            return "查詢失敗: " + e.getMessage();
        }
    }

    /**
     * 獲取資料庫連接
     */
    private Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
            return DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL driver not found", e);
        }
    }
}