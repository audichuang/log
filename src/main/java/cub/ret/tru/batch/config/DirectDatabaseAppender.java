package cub.ret.tru.batch.config;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 直接連接資料庫的 Log4j2 Appender 備用方案
 * 不依賴 Spring 容器，適用於 Spring 依賴注入失效的情況
 */
@Plugin(name = "DirectDatabaseAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class DirectDatabaseAppender extends AbstractAppender {

    // 資料庫連接配置 - 您可以根據實際情況調整
    private static final String DB_URL = "jdbc:postgresql://192.168.31.247:5444/postgres?useUnicode=true&characterEncoding=utf8&useSSL=false&currentSchema=public";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "VZq9rWbC3oJYFYdDrjT6edewVHQEKNCBWPDnyqxKyzMTE3CoozBrWnYsi6KkpwKujcFKDytQCrxhTbcxsAB2vswcVgQc9ieYvtpP";

    private static final String INSERT_SQL = """
            INSERT INTO batch_log (execution_id, job_name, step_name, log_level,
                                  message, logger_name, thread_name, exception_stack,
                                  log_time, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    // 非同步處理日誌的佇列
    private final BlockingQueue<LogEvent> logQueue = new LinkedBlockingQueue<>(10000);
    private final Thread logProcessor;
    private volatile boolean isShutdown = false;

    protected DirectDatabaseAppender(String name, Filter filter) {
        super(name, filter, null, true, null);

        // 啟動日誌處理執行緒
        this.logProcessor = new Thread(this::processLogs, "DirectDatabaseAppender-Worker");
        this.logProcessor.setDaemon(true);
        this.logProcessor.start();

        LOGGER.info("DirectDatabaseAppender initialized successfully");
    }

    @Override
    public void append(LogEvent event) {
        try {
            // 檢查是否有執行代號，沒有則跳過
            String executionId = event.getContextData().getValue("executionId");
            if (executionId == null) {
                System.out.println("DirectDatabaseAppender: Skipping log event without executionId: " +
                        event.getMessage().getFormattedMessage());
                return;
            }

            // 調試信息
            System.out.println("DirectDatabaseAppender: Adding log to queue with executionId: " + executionId);

            // 非同步處理，避免阻塞主執行緒
            if (!logQueue.offer(event.toImmutable())) {
                System.err.println("DirectDatabaseAppender: Log queue is full, dropping log event");
            } else {
                System.out.println(
                        "DirectDatabaseAppender: Log successfully added to queue, queue size: " + logQueue.size());
            }
        } catch (Exception e) {
            System.err.println("DirectDatabaseAppender: Failed to append log: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 處理日誌佇列中的事件
     */
    private void processLogs() {
        while (!isShutdown) {
            try {
                LogEvent event = logQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    saveLogToDatabase(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("DirectDatabaseAppender: Error processing log: " + e.getMessage());
            }
        }

        // 處理剩餘的日誌
        while (!logQueue.isEmpty()) {
            try {
                LogEvent event = logQueue.poll();
                if (event != null) {
                    saveLogToDatabase(event);
                }
            } catch (Exception e) {
                System.err.println("DirectDatabaseAppender: Error processing remaining log: " + e.getMessage());
            }
        }
    }

    /**
     * 將日誌保存到資料庫
     */
    private void saveLogToDatabase(LogEvent event) {
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {

            String executionId = event.getContextData().getValue("executionId");
            String jobName = event.getContextData().getValue("jobName");
            String stepName = event.getContextData().getValue("stepName");
            String logLevel = event.getLevel().name();
            String message = event.getMessage().getFormattedMessage();
            String loggerName = event.getLoggerName();
            String threadName = event.getThreadName();
            String exceptionStack = null;

            // 處理異常資訊
            if (event.getThrown() != null) {
                StringBuilder sb = new StringBuilder();
                Throwable throwable = event.getThrown();
                sb.append(throwable.toString()).append("\n");
                for (StackTraceElement element : throwable.getStackTrace()) {
                    sb.append("\tat ").append(element.toString()).append("\n");
                }
                exceptionStack = sb.toString();
            }

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

            // 調試信息
            System.out.println("DirectDatabaseAppender: Successfully saved log with executionId: " + executionId +
                    ", rowsAffected: " + rowsAffected);

        } catch (SQLException e) {
            System.err.println("DirectDatabaseAppender: Failed to save log to database: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("DirectDatabaseAppender: Unexpected error: " + e.getMessage());
            e.printStackTrace();
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

    @Override
    public void stop() {
        super.stop();
        isShutdown = true;

        // 等待處理執行緒結束
        try {
            if (logProcessor != null) {
                logProcessor.join(5000); // 等待最多5秒
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        LOGGER.info("DirectDatabaseAppender stopped");
    }

    @PluginFactory
    public static DirectDatabaseAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter) {

        if (name == null) {
            LOGGER.error("No name provided for DirectDatabaseAppender");
            return null;
        }

        return new DirectDatabaseAppender(name, filter);
    }
}