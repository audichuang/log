package cub.ret.tru.batch.service;

import cub.ret.tru.batch.dto.LogQueryCriteria;
import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.repository.BatchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OptimizedLogStreamService {

    private final LogQueryService logQueryService;
    private final BatchLogRepository repository;
    
    private final Map<String, SseConnection> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pollingTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    
    // 添加連接清理任務
    private ScheduledFuture<?> cleanupTask;
    
    @PostConstruct
    public void init() {
        // 每 5 分鐘檢查一次死連接
        this.cleanupTask = scheduler.scheduleWithFixedDelay(() -> {
            cleanupDeadConnections();
        }, 5, 5, TimeUnit.MINUTES);
        
        log.info("OptimizedLogStreamService 已初始化，啟動定期清理任務");
    }

    public SseEmitter createConnection(String connectionId, LogQueryCriteria criteria) {
        log.info("建立 SSE 連接: {}, 目前活躍連接數: {}", connectionId, activeConnections.size());
        
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 分鐘 = 1800000ms
        SseConnection connection = new SseConnection(emitter, criteria, LocalDateTime.now());
        
        setupEmitterCallbacks(connectionId, emitter);
        activeConnections.put(connectionId, connection);
        
        sendInitialData(connectionId);
        
        if (shouldStartPolling(criteria)) {
            startPolling(connectionId);
        }
        
        // 啟動心跳機制
        startHeartbeat(connectionId);

        log.info("SSE 連接已建立: {}, 總連接數: {}, 超時設定: 30分鐘", connectionId, activeConnections.size());
        return emitter;
    }
    
    private void setupEmitterCallbacks(String connectionId, SseEmitter emitter) {
        emitter.onCompletion(() -> {
            log.info("SSE 連接正常完成: {}", connectionId);
            cleanupConnection(connectionId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE 連接超時: {}", connectionId);
            cleanupConnection(connectionId);
        });
        emitter.onError((ex) -> {
            log.error("SSE 連接錯誤: {}", connectionId, ex);
            cleanupConnection(connectionId);
        });
    }
    
    private void sendInitialData(String connectionId) {
        SseConnection connection = activeConnections.get(connectionId);
        if (connection == null) {
            log.warn("連接不存在，無法發送初始數據: {}", connectionId);
            return;
        }
        
        try {
            Map<String, Object> connectionInfo = Map.of(
                "message", "連接已建立",
                "connectionId", connectionId,
                "timestamp", LocalDateTime.now()
            );
            connection.emitter.send(SseEmitter.event().name("connected").data(connectionInfo));
            
            List<BatchLogEntity> logs = logQueryService.queryLogs(connection.criteria);
            if (!logs.isEmpty()) {
                connection.emitter.send(SseEmitter.event().name("logs").data(logs));
                log.debug("發送初始日誌數據: {} 條, 連接: {}", logs.size(), connectionId);
            }
        } catch (Exception e) {
            log.error("發送初始數據失敗: {}", connectionId, e);
            cleanupConnection(connectionId);
        }
    }
    
    private boolean shouldStartPolling(LogQueryCriteria criteria) {
        return criteria.getExecutionId() == null || 
               LogQueryCriteria.LogQueryType.REAL_TIME.equals(criteria.getQueryType());
    }
    
    @Async
    public void startPolling(String connectionId) {
        ScheduledFuture<?> pollingTask = scheduler.scheduleWithFixedDelay(() -> {
            SseConnection connection = activeConnections.get(connectionId);
            if (connection == null) {
                log.debug("連接已關閉，停止輪詢: {}", connectionId);
                return;
            }
            
            try {
                List<BatchLogEntity> newLogs = repository.findRecentLogsAfter(connection.lastQueryTime, 50);
                
                if (!newLogs.isEmpty()) {
                    connection.emitter.send(SseEmitter.event().name("logs").data(newLogs));
                    connection.lastQueryTime = LocalDateTime.now();
                    log.debug("發送新日誌: {} 條, 連接: {}", newLogs.size(), connectionId);
                }
            } catch (IOException e) {
                log.error("發送日誌失敗，關閉連接: {}", connectionId, e);
                cleanupConnection(connectionId);
            } catch (Exception e) {
                log.error("輪詢失敗: {}, 錯誤: {}", connectionId, e.getMessage());
            }
        }, 3, 3, TimeUnit.SECONDS);
        
        pollingTasks.put(connectionId, pollingTask);
        log.info("開始輪詢任務: {}", connectionId);
    }
    
    /**
     * 啟動心跳機制
     * 每30秒發送一次心跳，檢查連接是否還活著
     */
    private void startHeartbeat(String connectionId) {
        ScheduledFuture<?> heartbeatTask = scheduler.scheduleWithFixedDelay(() -> {
            SseConnection connection = activeConnections.get(connectionId);
            if (connection == null) {
                log.debug("連接已關閉，停止心跳: {}", connectionId);
                return;
            }
            
            try {
                // 發送心跳事件
                Map<String, Object> heartbeat = Map.of(
                    "type", "heartbeat",
                    "timestamp", LocalDateTime.now(),
                    "connectionId", connectionId
                );
                
                connection.emitter.send(SseEmitter.event().name("heartbeat").data(heartbeat));
                connection.lastHeartbeatTime = LocalDateTime.now();
                log.debug("發送心跳成功: {}", connectionId);
                
            } catch (IOException e) {
                log.warn("心跳發送失敗，連接可能已斷開，清理連接: {}", connectionId, e);
                cleanupConnection(connectionId);
            } catch (Exception e) {
                log.error("心跳發送異常: {}, 錯誤: {}", connectionId, e.getMessage());
                // 如果是嚴重錯誤，也清理連接
                if (e instanceof IllegalStateException) {
                    log.warn("連接狀態異常，清理連接: {}", connectionId);
                    cleanupConnection(connectionId);
                }
            }
        }, 30, 30, TimeUnit.SECONDS);  // 首次延遲30秒，然後每30秒執行一次
        
        heartbeatTasks.put(connectionId, heartbeatTask);
        log.info("啟動心跳任務: {}", connectionId);
    }
    
    public void updateFilter(String connectionId, LogQueryCriteria newCriteria) {
        SseConnection connection = activeConnections.get(connectionId);
        if (connection != null) {
            log.info("更新過濾條件: {}", connectionId);
            connection.criteria = newCriteria;
            sendInitialData(connectionId);
        } else {
            log.warn("嘗試更新不存在的連接過濾條件: {}", connectionId);
        }
    }
    
    private void cleanupConnection(String connectionId) {
        SseConnection connection = activeConnections.remove(connectionId);
        
        // 清理輪詢任務
        ScheduledFuture<?> pollingTask = pollingTasks.remove(connectionId);
        if (pollingTask != null && !pollingTask.isCancelled()) {
            boolean cancelled = pollingTask.cancel(true);
            log.info("輪詢任務已取消: {}, 成功: {}", connectionId, cancelled);
        }
        
        // 清理心跳任務
        ScheduledFuture<?> heartbeatTask = heartbeatTasks.remove(connectionId);
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            boolean cancelled = heartbeatTask.cancel(true);
            log.info("心跳任務已取消: {}, 成功: {}", connectionId, cancelled);
        }
        
        if (connection != null) {
            try {
                connection.emitter.complete();
            } catch (Exception e) {
                log.debug("關閉 SSE 發射器時出錯: {}", connectionId, e);
            }
        }
        
        log.info("連接已完全清理: {}, 剩餘活躍連接: {}, 剩餘輪詢任務: {}, 剩餘心跳任務: {}", 
                connectionId, activeConnections.size(), pollingTasks.size(), heartbeatTasks.size());
    }
    
    public void closeConnection(String connectionId) {
        log.info("手動關閉連接: {}", connectionId);
        cleanupConnection(connectionId);
    }
    
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
    
    public int getActivePollingTaskCount() {
        return pollingTasks.size();
    }
    
    public int getActiveHeartbeatTaskCount() {
        return heartbeatTasks.size();
    }
    
    public void cleanupAllConnections() {
        log.info("清理所有連接，當前連接數: {}, 輪詢任務數: {}, 心跳任務數: {}", 
                activeConnections.size(), pollingTasks.size(), heartbeatTasks.size());
        
        Set<String> connectionIds = new HashSet<>(activeConnections.keySet());
        for (String connectionId : connectionIds) {
            cleanupConnection(connectionId);
        }
        
        log.info("所有連接已清理完成");
    }
    
    private void cleanupDeadConnections() {
        log.info("清理死連接，當前連接數: {}, 輪詢任務數: {}, 心跳任務數: {}", 
                activeConnections.size(), pollingTasks.size(), heartbeatTasks.size());
        
        Set<String> connectionIds = new HashSet<>(activeConnections.keySet());
        for (String connectionId : connectionIds) {
            SseConnection connection = activeConnections.get(connectionId);
            if (connection == null) {
                cleanupConnection(connectionId);
                continue;
            }
            
            // 檢查連接是否超過最大空閒時間（30分鐘）
            LocalDateTime now = LocalDateTime.now();
            if (connection.lastQueryTime.plusMinutes(30).isBefore(now)) {
                log.warn("連接 {} 已超過最大空閒時間，執行清理", connectionId);
                cleanupConnection(connectionId);
            }
        }
        
        log.info("所有死連接已清理完成");
    }
    
    private static class SseConnection {
        final SseEmitter emitter;
        LogQueryCriteria criteria;
        LocalDateTime lastQueryTime;
        LocalDateTime lastHeartbeatTime;
        
        SseConnection(SseEmitter emitter, LogQueryCriteria criteria, LocalDateTime lastQueryTime) {
            this.emitter = emitter;
            this.criteria = criteria;
            this.lastQueryTime = lastQueryTime;
            this.lastHeartbeatTime = LocalDateTime.now();
        }
    }
}