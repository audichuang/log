package cub.ret.tru.batch.controller;

import cub.ret.tru.batch.entity.BatchExecutionEntity;
import cub.ret.tru.batch.entity.BatchJobEntity;
import cub.ret.tru.batch.service.BatchJobService;
import cub.ret.tru.batch.service.LogStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 批次作業管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class BatchJobController {

    private final BatchJobService batchJobService;
    private final LogStreamService logStreamService;

    /**
     * 初始化數據
     */
    @PostConstruct
    public void init() {
        batchJobService.initializeJobs();
    }

    /**
     * 獲取所有批次作業
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<BatchJobEntity>> getAllJobs() {
        try {
            List<BatchJobEntity> jobs = batchJobService.getAllJobs();
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            log.error("查詢批次作業失敗", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根據 ID 查詢作業
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<BatchJobEntity> getJobById(@PathVariable String jobId) {
        try {
            BatchJobEntity job = batchJobService.getJobById(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(job);
        } catch (Exception e) {
            log.error("查詢作業失敗: {}", jobId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 執行批次作業
     */
    @PostMapping("/jobs/{jobId}/run")
    public ResponseEntity<BatchExecutionEntity> runJob(@PathVariable String jobId) {
        try {
            BatchExecutionEntity execution = batchJobService.runJob(jobId);
            return ResponseEntity.ok(execution);
        } catch (IllegalArgumentException e) {
            log.warn("執行作業失敗 - 參數錯誤: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            log.warn("執行作業失敗 - 狀態錯誤: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("執行作業失敗: {}", jobId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 停止批次作業
     */
    @PostMapping("/executions/{executionId}/stop")
    public ResponseEntity<String> stopJob(@PathVariable String executionId) {
        try {
            batchJobService.stopJob(executionId);
            return ResponseEntity.ok("批次作業已停止");
        } catch (IllegalArgumentException e) {
            log.warn("停止作業失敗 - 參數錯誤: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("停止作業失敗 - 狀態錯誤: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("停止作業失敗: {}", executionId, e);
            return ResponseEntity.internalServerError()
                    .body("停止作業失敗: " + e.getMessage());
        }
    }

    /**
     * 查詢執行記錄
     */
    @GetMapping("/executions/{executionId}")
    public ResponseEntity<BatchExecutionEntity> getExecution(@PathVariable String executionId) {
        try {
            BatchExecutionEntity execution = batchJobService.getExecution(executionId);
            if (execution == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(execution);
        } catch (Exception e) {
            log.error("查詢執行記錄失敗: {}", executionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 查詢作業執行歷史
     */
    @GetMapping("/jobs/{jobName}/executions")
    public ResponseEntity<List<BatchExecutionEntity>> getJobExecutions(@PathVariable String jobName) {
        try {
            List<BatchExecutionEntity> executions = batchJobService.getJobExecutions(jobName);
            return ResponseEntity.ok(executions);
        } catch (Exception e) {
            log.error("查詢作業執行歷史失敗: {}", jobName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 建立 SSE 連接來串流日誌
     */
    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(
            @RequestParam(required = false) String executionId,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        
        String connectionId = UUID.randomUUID().toString();
        log.info("建立日誌串流連接: {} (executionId: {}, jobName: {})", 
                connectionId, executionId, jobName);

        // 創建過濾條件
        LogStreamService.LogFilter filter = new LogStreamService.LogFilter();
        filter.setExecutionId(executionId);
        filter.setJobName(jobName);
        filter.setLogLevel(logLevel);
        filter.setKeyword(keyword);
        
        // 解析時間參數
        if (startTime != null && !startTime.isEmpty()) {
            try {
                filter.setStartTime(java.time.LocalDateTime.parse(startTime));
            } catch (Exception e) {
                log.warn("解析開始時間失敗: {}", startTime);
            }
        }
        if (endTime != null && !endTime.isEmpty()) {
            try {
                filter.setEndTime(java.time.LocalDateTime.parse(endTime));
            } catch (Exception e) {
                log.warn("解析結束時間失敗: {}", endTime);
            }
        }

        return logStreamService.createConnection(connectionId, filter);
    }

    /**
     * 更新 SSE 連接的過濾條件
     */
    @PostMapping("/logs/stream/{connectionId}/filter")
    public ResponseEntity<String> updateStreamFilter(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> filterParams) {
        
        try {
            LogStreamService.LogFilter filter = new LogStreamService.LogFilter();
            filter.setExecutionId((String) filterParams.get("executionId"));
            filter.setJobName((String) filterParams.get("jobName"));
            filter.setLogLevel((String) filterParams.get("logLevel"));
            filter.setKeyword((String) filterParams.get("keyword"));
            
            // 解析時間參數
            if (filterParams.get("startTime") != null) {
                filter.setStartTime(java.time.LocalDateTime.parse(filterParams.get("startTime").toString()));
            }
            if (filterParams.get("endTime") != null) {
                filter.setEndTime(java.time.LocalDateTime.parse(filterParams.get("endTime").toString()));
            }

            logStreamService.updateFilter(connectionId, filter);
            return ResponseEntity.ok("過濾條件已更新");
        } catch (Exception e) {
            log.error("更新過濾條件失敗: {}", connectionId, e);
            return ResponseEntity.badRequest().body("更新失敗: " + e.getMessage());
        }
    }

    /**
     * 關閉 SSE 連接
     */
    @DeleteMapping("/logs/stream/{connectionId}")
    public ResponseEntity<String> closeStream(@PathVariable String connectionId) {
        try {
            logStreamService.closeConnection(connectionId);
            return ResponseEntity.ok("連接已關閉");
        } catch (Exception e) {
            log.error("關閉連接失敗: {}", connectionId, e);
            return ResponseEntity.internalServerError().body("關閉失敗: " + e.getMessage());
        }
    }

    /**
     * 獲取 SSE 連接狀態
     */
    @GetMapping("/logs/stream/status")
    public ResponseEntity<Map<String, Object>> getStreamStatus() {
        int activeConnections = logStreamService.getActiveConnectionCount();
        return ResponseEntity.ok(Map.of(
                "activeConnections", activeConnections,
                "timestamp", System.currentTimeMillis()
        ));
    }
} 