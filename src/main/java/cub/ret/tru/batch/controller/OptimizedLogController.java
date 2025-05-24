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
            @RequestParam(defaultValue = "HISTORICAL") LogQueryCriteria.LogQueryType queryType) {
        
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
                    .build();
            
            List<BatchLogEntity> logs = logQueryService.queryLogs(criteria);
            
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(logs.size()))
                    .body(logs);
                    
        } catch (Exception e) {
            log.error("查詢日誌失敗", e);
            return ResponseEntity.internalServerError().build();
        }
    }

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

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(
            @RequestParam(required = false) String executionId,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        String connectionId = UUID.randomUUID().toString();

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

    @GetMapping("/stream/status")
    public ResponseEntity<Map<String, Object>> getStreamStatus() {
        return ResponseEntity.ok(Map.of(
                "activeConnections", streamService.getActiveConnectionCount(),
                "timestamp", System.currentTimeMillis(),
                "serverTime", LocalDateTime.now()
        ));
    }
}