package cub.ret.tru.batch.service;

import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.repository.BatchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 日誌串流服務 - 支援 SSE (Server-Sent Events)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogStreamService {

    private final BatchLogRepository batchLogRepository;
    
    // 儲存活躍的 SSE 連接
    private final Map<String, SseEmitter> activeConnections = new ConcurrentHashMap<>();
    
    // 儲存每個連接的最後日誌時間
    private final Map<String, LocalDateTime> lastLogTime = new ConcurrentHashMap<>();
    
    // 儲存每個連接的過濾條件
    private final Map<String, LogFilter> connectionFilters = new ConcurrentHashMap<>();
    
    // 定時執行器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 創建 SSE 連接
     */
    public SseEmitter createConnection(String connectionId, LogFilter filter) {
        log.info("建立 SSE 連接: {}", connectionId);
        
        SseEmitter emitter = new SseEmitter(0L); // 無限超時
        
        // 設置回調
        emitter.onCompletion(() -> {
            log.info("SSE 連接完成: {}", connectionId);
            cleanupConnection(connectionId);
        });
        
        emitter.onTimeout(() -> {
            log.warn("SSE 連接超時: {}", connectionId);
            cleanupConnection(connectionId);
        });
        
        emitter.onError((ex) -> {
            log.error("SSE 連接錯誤: {} - {}", connectionId, ex.getMessage());
            cleanupConnection(connectionId);
        });

        // 儲存連接和過濾條件
        activeConnections.put(connectionId, emitter);
        connectionFilters.put(connectionId, filter);
        lastLogTime.put(connectionId, LocalDateTime.now().minusMinutes(1)); // 從1分鐘前開始

        // 發送初始訊息
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("SSE 連接已建立"));
        } catch (IOException e) {
            log.error("發送初始訊息失敗: {}", connectionId, e);
            cleanupConnection(connectionId);
        }

        // 開始輪詢（如果是輪詢類型的查詢）或立即查詢
        queryAndSendLogs(connectionId, filter);
        
        // 如果沒有特定的執行ID過濾，開始輪詢新日誌
        if (filter == null || ObjectUtils.isEmpty(filter.getExecutionId())) {
            startPolling(connectionId);
        }

        return emitter;
    }

    /**
     * 更新連接的過濾條件
     */
    public void updateFilter(String connectionId, LogFilter filter) {
        if (!activeConnections.containsKey(connectionId)) {
            log.warn("連接不存在: {}", connectionId);
            return;
        }

        log.info("更新過濾條件: {}", connectionId);
        connectionFilters.put(connectionId, filter);

        // 如果過濾條件為空，開始輪詢
        if (filter == null || isEmptyFilter(filter)) {
            startPolling(connectionId);
        } else {
            // 如果有過濾條件，立即查詢並發送
            queryAndSendLogs(connectionId, filter);
        }
    }

    /**
     * 開始輪詢日誌
     */
    @Async
    public void startPolling(String connectionId) {
        scheduler.scheduleWithFixedDelay(() -> {
            if (!activeConnections.containsKey(connectionId)) {
                return; // 連接已關閉
            }

            LogFilter filter = connectionFilters.get(connectionId);
            queryAndSendLogs(connectionId, filter);
        }, 1, 2, TimeUnit.SECONDS); // 每2秒輪詢一次
    }

    /**
     * 查詢並發送日誌
     */
    private void queryAndSendLogs(String connectionId, LogFilter filter) {
        SseEmitter emitter = activeConnections.get(connectionId);
        if (emitter == null) {
            return;
        }

        try {
            List<BatchLogEntity> logs = queryLogsByFilter(filter);

            if (!logs.isEmpty()) {
                // 發送日誌數據
                emitter.send(SseEmitter.event()
                        .name("logs")
                        .data(logs));

                log.debug("發送 {} 條日誌到連接: {}", logs.size(), connectionId);
            }

        } catch (IOException e) {
            log.error("發送日誌失敗，關閉連接: {}", connectionId, e);
            cleanupConnection(connectionId);
        } catch (Exception e) {
            log.error("查詢日誌失敗: {}", connectionId, e);
        }
    }

    /**
     * 根據過濾條件查詢日誌
     */
    private List<BatchLogEntity> queryLogsByFilter(LogFilter filter) {
        // 如果過濾條件為空，返回最近的日誌
//            if (filter == null || isEmptyFilter(filter)) {
//                return batchLogRepository.findRecentLogs();
//            }

        // 優先使用 executionId 查詢
        if (!ObjectUtils.isEmpty(filter.getExecutionId())) {
            return batchLogRepository.findByExecutionIdOrderByLogTimeDesc(filter.getExecutionId());
        }
        
        // 其次使用 jobName 查詢
//        if (!ObjectUtils.isEmpty(filter.getJobName())) {
//            return batchLogRepository.findByJobNameOrderByLogTimeDesc(filter.getJobName());
//        }
//
        // 如果有時間範圍
//        if (filter.getStartTime() != null && filter.getEndTime() != null) {
//            return batchLogRepository.findByLogTimeBetweenOrderByLogTimeDesc(filter.getStartTime(), filter.getEndTime());
//        }
        
        // 使用 logLevel 查詢
//        if (!ObjectUtils.isEmpty(filter.getLogLevel())) {
//            return batchLogRepository.findByLogLevelOrderByLogTimeDesc(filter.getLogLevel());
//        }
        
        // 如果有關鍵字，使用關鍵字查詢
//        if (!ObjectUtils.isEmpty(filter.getKeyword())) {
//            return batchLogRepository.findByKeywordOrderByLogTimeDesc(filter.getKeyword());
//        }
        
        // 預設返回最近的日誌
//        return batchLogRepository.findRecentLogs();
        return null;
    }

    /**
     * 檢查是否為空過濾條件
     */
    private boolean isEmptyFilter(LogFilter filter) {
        return ObjectUtils.isEmpty(filter.getExecutionId()) && 
               ObjectUtils.isEmpty(filter.getJobName()) && 
               ObjectUtils.isEmpty(filter.getLogLevel()) && 
               ObjectUtils.isEmpty(filter.getKeyword()) && 
               filter.getStartTime() == null && 
               filter.getEndTime() == null;
    }

    /**
     * 清理連接
     */
    private void cleanupConnection(String connectionId) {
        activeConnections.remove(connectionId);
        lastLogTime.remove(connectionId);
        connectionFilters.remove(connectionId);
        log.info("已清理連接: {}", connectionId);
    }

    /**
     * 關閉連接
     */
    public void closeConnection(String connectionId) {
        SseEmitter emitter = activeConnections.get(connectionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("關閉連接失敗: {}", connectionId, e);
            }
        }
        cleanupConnection(connectionId);
    }

    /**
     * 獲取活躍連接數
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    /**
     * 日誌過濾條件
     */
    public static class LogFilter {
        private String executionId;
        private String jobName;
        private String logLevel;
        private String keyword;
        private LocalDateTime startTime;
        private LocalDateTime endTime;

        // Getters and Setters
        public String getExecutionId() { return executionId; }
        public void setExecutionId(String executionId) { this.executionId = executionId; }
        public String getJobName() { return jobName; }
        public void setJobName(String jobName) { this.jobName = jobName; }
        public String getLogLevel() { return logLevel; }
        public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    }
} 