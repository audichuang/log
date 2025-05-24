package cub.ret.tru.batch.controller;

import cub.ret.tru.batch.util.LogAppenderSwitcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日誌測試控制器
 * 用於測試不同的日誌 Appender 方案
 */
@Slf4j
@RestController
@RequestMapping("/api/log-test")
@RequiredArgsConstructor
public class LogTestController {

    private final LogAppenderSwitcher logAppenderSwitcher;

    /**
     * 測試當前日誌配置
     */
    @PostMapping("/test-current")
    public String testCurrentConfiguration() {
        // 設置 MDC 上下文（必要的，否則日誌不會記錄到資料庫）
        String executionId = "TEST_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        MDC.put("executionId", executionId);
        MDC.put("jobName", "LOG_TEST_JOB");
        MDC.put("stepName", "LOG_TEST_STEP");

        try {
            log.info("=== 開始日誌測試 ===");
            log.info("執行代號: {}", executionId);
            log.debug("這是一條 DEBUG 日誌");
            log.info("這是一條 INFO 日誌");
            log.warn("這是一條 WARN 日誌");
            log.error("這是一條 ERROR 日誌");

            // 測試異常記錄
            try {
                throw new RuntimeException("測試異常記錄");
            } catch (Exception e) {
                log.error("捕獲到測試異常", e);
            }

            log.info("=== 日誌測試完成 ===");

            return "日誌測試完成，執行代號: " + executionId + "。請檢查資料庫 batch_log 表格是否有新記錄。";
        } finally {
            MDC.clear();
        }
    }

    /**
     * 切換到直接資料庫連接方案
     */
    @PostMapping("/switch-to-direct")
    public String switchToDirectDatabaseAppender() {
        try {
            logAppenderSwitcher.switchToDirectDatabaseAppender();
            return "已切換至 DirectDatabaseAppender 配置。這是不依賴 Spring 容器的備用方案。";
        } catch (Exception e) {
            return "切換失敗: " + e.getMessage();
        }
    }

    /**
     * 切換回 Spring 依賴注入方案
     */
    @PostMapping("/switch-to-spring")
    public String switchToSpringDatabaseAppender() {
        try {
            logAppenderSwitcher.switchToSpringDatabaseAppender();
            return "已切換至 Spring DatabaseAppender 配置。這是原始的依賴注入方案。";
        } catch (Exception e) {
            return "切換失敗: " + e.getMessage();
        }
    }

    /**
     * 檢查當前配置狀態
     */
    @GetMapping("/check-config")
    public String checkCurrentConfiguration() {
        try {
            logAppenderSwitcher.checkCurrentConfiguration();
            return "配置檢查完成，請查看控制台輸出。";
        } catch (Exception e) {
            return "檢查配置失敗: " + e.getMessage();
        }
    }

    /**
     * 批量測試日誌記錄（用於性能測試）
     */
    @PostMapping("/batch-test/{count}")
    public String batchLogTest(@PathVariable int count) {
        String executionId = "BATCH_TEST_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        MDC.put("executionId", executionId);
        MDC.put("jobName", "BATCH_LOG_TEST_JOB");
        MDC.put("stepName", "BATCH_LOG_TEST_STEP");

        try {
            long startTime = System.currentTimeMillis();

            for (int i = 1; i <= count; i++) {
                log.info("批量測試日誌 #{}: 當前時間 {}", i, LocalDateTime.now());

                if (i % 10 == 0) {
                    log.warn("每10條的警告日誌 #{}", i);
                }

                if (i % 50 == 0) {
                    log.error("每50條的錯誤日誌 #{}", i);
                }
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            log.info("批量測試完成: 記錄了 {} 條日誌，耗時 {} 毫秒", count, duration);

            return String.format("批量日誌測試完成！記錄了 %d 條日誌，耗時 %d 毫秒，執行代號: %s",
                    count, duration, executionId);
        } finally {
            MDC.clear();
        }
    }
}