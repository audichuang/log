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
            @RequestParam(defaultValue = "HISTORICAL") LogQueryCriteria.LogQueryType queryType,
            @RequestParam(required = false) Long lastId) { // 🔥 添加 lastId 參數

        try {
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
                    .lastId(lastId) // 🔥 設置 lastId
                    .build();

            List<BatchLogEntity> logs = logQueryService.queryLogs(criteria);

            // 🔥 添加調試信息
            log.debug("查詢完成: executionId={}, jobName={}, lastId={}, 結果數量={}",
                    executionId, jobName, lastId, logs.size());

            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(logs.size()))
                    .header("Cache-Control", "no-cache") // 🔥 防止快取
                    .body(logs);

        } catch (Exception e) {
            log.error("查詢日誌失敗: executionId={}, jobName={}, lastId={}",
                    executionId, jobName, lastId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/query")
    public ResponseEntity<List<BatchLogEntity>> queryLogsPost(@RequestBody LogQueryCriteria criteria) {
        try {
            // 🔥 添加調試信息
            log.debug("POST查詢: executionId={}, jobName={}, lastId={}, limit={}",
                    criteria.getExecutionId(), criteria.getJobName(),
                    criteria.getLastId(), criteria.getLimit());

            List<BatchLogEntity> logs = logQueryService.queryLogs(criteria);

            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache")
                    .header("X-Total-Count", String.valueOf(logs.size()))
                    .body(logs);
        } catch (Exception e) {
            log.error("POST查詢日誌失敗: criteria={}", criteria, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // 🔥 可選：添加專門的增量查詢 endpoint
    @GetMapping("/incremental")
    public ResponseEntity<List<BatchLogEntity>> getIncrementalLogs(
            @RequestParam Long lastId,
            @RequestParam(required = false) String executionId,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) List<String> logLevels,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") Integer limit) {

        try {
            LogQueryCriteria criteria = LogQueryCriteria.builder()
                    .executionId(executionId)
                    .jobName(jobName)
                    .logLevels(logLevels)
                    .keyword(keyword)
                    .lastId(lastId)
                    .limit(limit)
                    .sortDirection("ASC") // 增量查詢固定用升序
                    .queryType(LogQueryCriteria.LogQueryType.REAL_TIME)
                    .build();

            List<BatchLogEntity> logs = logQueryService.queryLogs(criteria);

            log.debug("增量查詢完成: lastId={}, executionId={}, jobName={}, 新日誌數量={}",
                    lastId, executionId, jobName, logs.size());

            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(logs.size()))
                    .header("Cache-Control", "no-cache")
                    .body(logs);

        } catch (Exception e) {
            log.error("增量查詢失敗: lastId={}, executionId={}, jobName={}",
                    lastId, executionId, jobName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // 其他方法保持不變...
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(
            @RequestParam(required = false) String executionId,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) List<String> logLevels,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        String connectionId = UUID.randomUUID().toString();
        log.info("建立 SSE 連接: {}, 參數: executionId={}, jobName={}, logLevels={}, keyword={}",
                connectionId, executionId, jobName, logLevels, keyword);

        LogQueryCriteria criteria = LogQueryCriteria.builder()
                .executionId(executionId)
                .jobName(jobName)
                .logLevels(logLevels)
                .keyword(keyword)
                .startTime(startTime)
                .endTime(endTime)
                .queryType(LogQueryCriteria.LogQueryType.REAL_TIME)
                .limit(50)
                .build();

        return streamService.createConnection(connectionId, criteria);
    }

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

    @DeleteMapping("/stream/{connectionId}")
    public ResponseEntity<String> closeStream(@PathVariable String connectionId) {
        try {
            streamService.closeConnection(connectionId);
            return ResponseEntity.ok("連接已關閉");
        } catch (Exception e) {
            log.error("關閉連接失敗: {}", connectionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/stream/cleanup")
    public ResponseEntity<String> cleanupAllConnections() {
        try {
            streamService.cleanupAllConnections();
            return ResponseEntity.ok("所有連接已清理完成");
        } catch (Exception e) {
            log.error("清理所有連接失敗", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/stream/status")
    public ResponseEntity<Map<String, Object>> getStreamStatus() {
        return ResponseEntity.ok(Map.of(
                "activeConnections", streamService.getActiveConnectionCount(),
                "activePollingTasks", streamService.getActivePollingTaskCount(),
                "timestamp", System.currentTimeMillis(),
                "serverTime", LocalDateTime.now()
        ));
    }
}