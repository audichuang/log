package cub.ret.tru.batch.service;

import cub.ret.tru.batch.dto.LogQueryCriteria;
import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.repository.BatchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OptimizedLogStreamService {

    private final LogQueryService logQueryService;
    private final BatchLogRepository repository; // 直接用於輪詢查詢

    private final Map<String, SseConnection> activeConnections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public SseEmitter createConnection(String connectionId, LogQueryCriteria criteria) {
        log.info("建立 SSE 連接: {}", connectionId);

        SseEmitter emitter = new SseEmitter(0L);
        SseConnection connection = new SseConnection(emitter, criteria, LocalDateTime.now());

        setupEmitterCallbacks(connectionId, emitter);
        activeConnections.put(connectionId, connection);

        // 發送初始數據
        sendInitialData(connectionId);

        // 開始輪詢（如果需要）
        if (shouldStartPolling(criteria)) {
            startPolling(connectionId);
        }

        return emitter;
    }

    private void setupEmitterCallbacks(String connectionId, SseEmitter emitter) {
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
    }

    private void sendInitialData(String connectionId) {
        SseConnection connection = activeConnections.get(connectionId);
        if (connection == null) return;

        try {
            // 發送連接確認
            connection.emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("SSE 連接已建立"));

            // 發送初始日誌數據
            List<BatchLogEntity> logs = logQueryService.queryLogs(connection.criteria);
            if (!logs.isEmpty()) {
                connection.emitter.send(SseEmitter.event()
                        .name("logs")
                        .data(logs));
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
        scheduler.scheduleWithFixedDelay(() -> {
            SseConnection connection = activeConnections.get(connectionId);
            if (connection == null) return;

            try {
                // 使用原生查詢進行增量輪詢
                List<BatchLogEntity> newLogs = repository.findRecentLogsAfter(
                        connection.lastQueryTime, 50);

                if (!newLogs.isEmpty()) {
                    connection.emitter.send(SseEmitter.event()
                            .name("logs")
                            .data(newLogs));

                    connection.lastQueryTime = LocalDateTime.now();
                }

            } catch (IOException e) {
                log.error("發送增量日誌失敗: {}", connectionId, e);
                cleanupConnection(connectionId);
            } catch (Exception e) {
                log.error("輪詢日誌失敗: {}", connectionId, e);
            }

        }, 1, 2, TimeUnit.SECONDS);
    }

    public void updateFilter(String connectionId, LogQueryCriteria newCriteria) {
        SseConnection connection = activeConnections.get(connectionId);
        if (connection == null) {
            log.warn("連接不存在: {}", connectionId);
            return;
        }

        connection.criteria = newCriteria;
        sendInitialData(connectionId);
    }

    private void cleanupConnection(String connectionId) {
        activeConnections.remove(connectionId);
        log.info("已清理連接: {}", connectionId);
    }

    public void closeConnection(String connectionId) {
        SseConnection connection = activeConnections.get(connectionId);
        if (connection != null) {
            try {
                connection.emitter.complete();
            } catch (Exception e) {
                log.warn("關閉連接失敗: {}", connectionId, e);
            }
        }
        cleanupConnection(connectionId);
    }

    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    private static class SseConnection {
        final SseEmitter emitter;
        LogQueryCriteria criteria;
        LocalDateTime lastQueryTime;

        SseConnection(SseEmitter emitter, LogQueryCriteria criteria, LocalDateTime lastQueryTime) {
            this.emitter = emitter;
            this.criteria = criteria;
            this.lastQueryTime = lastQueryTime;
        }
    }
}
