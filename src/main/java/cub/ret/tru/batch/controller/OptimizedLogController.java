package cub.ret.tru.batch.controller;

import cub.ret.tru.batch.dto.LogQueryCriteria;
import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.service.LogQueryService;
import cub.ret.tru.batch.service.OptimizedLogStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/batch/optimized-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class OptimizedLogController {

    private final LogQueryService logQueryService;
    private final OptimizedLogStreamService streamService;

    /**
     * 統一的日誌查詢端點
     */
    @GetMapping
    public ResponseEntity<List<BatchLogEntity>> queryLogs(
            @RequestParam(required = false) String executionId,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String stepName,
            @RequestParam(required = false) List<String> logLevels,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "100") Integer limit,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(defaultValue = "HISTORICAL") LogQueryCriteria.LogQueryType queryType) {
        
        try {
            // 參數驗證
            if (limit != null && (limit < 1 || limit > 1000)) {
                limit = 100;
            }
            
            LogQueryCriteria criteria = LogQueryCriteria.builder()
                    .executionId(executionId)
                    .jobName(jobName)
                    .stepName(stepName)
                    .logLevels(logLevels)
                    .keyword(keyword)
                    .startTime(startTime)
                    .endTime(endTime)
                    .limit(limit)
                    .sortDirection(sortDirection)
                    .queryType(queryType)
                    .build();
            
            List<BatchLogEntity> logs = logQueryService.queryLogs(criteria);
            
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(logs.size()))
                    .header("X-Query-Time", String.valueOf(System.currentTimeMillis()))
                    .body(logs);
                    
        } catch (IllegalArgumentException e) {
            log.warn("查詢參數錯誤: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("查詢日誌失敗", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST 方式的複雜查詢（支援更複雜的查詢條件）
     */
    @PostMapping("/query")
    public ResponseEntity<List<BatchLogEntity>> queryLogsPost(@RequestBody LogQueryCriteria criteria) {
        try {
            List<BatchLogEntity> logs = logQueryService.queryLogs(criteria);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("查詢日誌失敗", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 建立 SSE 日誌串流連接
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(
            @RequestParam(required = false) String executionId,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        String connectionId = UUID.randomUUID().toString();
        log.info("建立日誌串流連接: {}", connectionId);

        LogQueryCriteria criteria = LogQueryCriteria.builder()
                .executionId(executionId)
                .jobName(jobName)
                .logLevels(logLevel != null ? List.of(logLevel) : null)
                .keyword(keyword)
                .startTime(startTime)
                .endTime(endTime)
                .queryType(LogQueryCriteria.LogQueryType.REAL_TIME)
                .limit(50)
                .build();

        return streamService.createConnection(connectionId, criteria);
    }

    /**
     * 更新串流過濾條件
     */
    @PutMapping("/stream/{connectionId}/filter")
    public ResponseEntity<String> updateStreamFilter(
            @PathVariable String connectionId,
            @RequestBody LogQueryCriteria criteria) {
        
        try {
            streamService.updateFilter(connectionId, criteria);
            return ResponseEntity.ok("過濾條件已更新");
        } catch (Exception e) {
            log.error("更新過濾條件失敗: {}", connectionId, e);
            return ResponseEntity.badRequest().body("更新失敗: " + e.getMessage());
        }
    }

    /**
     * 關閉串流連接
     */
    @DeleteMapping("/stream/{connectionId}")
    public ResponseEntity<String> closeStream(@PathVariable String connectionId) {
        try {
            streamService.closeConnection(connectionId);
            return ResponseEntity.ok("連接已關閉");
        } catch (Exception e) {
            log.error("關閉連接失敗: {}", connectionId, e);
            return ResponseEntity.internalServerError().body("關閉失敗: " + e.getMessage());
        }
    }

    /**
     * 獲取串流狀態
     */
    @GetMapping("/stream/status")
    public ResponseEntity<Map<String, Object>> getStreamStatus() {
        int activeConnections = streamService.getActiveConnectionCount();
        return ResponseEntity.ok(Map.of(
                "activeConnections", activeConnections,
                "timestamp", System.currentTimeMillis(),
                "serverTime", LocalDateTime.now()
        ));
    }

    /**
     * 日誌統計端點
     */
    @GetMapping("/stats/{executionId}")
    public ResponseEntity<Map<String, Object>> getLogStats(@PathVariable String executionId) {
        try {
            // 這裡可以加入統計邏輯
            LogQueryCriteria criteria = LogQueryCriteria.builder()
                    .executionId(executionId)
                    .build();
            
            List<BatchLogEntity> logs = logQueryService.queryLogs(criteria);
            
            Map<String, Long> levelCounts = logs.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            BatchLogEntity::getLogLevel,
                            java.util.stream.Collectors.counting()));
            
            return ResponseEntity.ok(Map.of(
                    "totalCount", logs.size(),
                    "levelCounts", levelCounts,
                    "executionId", executionId
            ));
        } catch (Exception e) {
            log.error("查詢日誌統計失敗: {}", executionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 