package cub.ret.tru.batch.controller;

import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.service.BatchLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批次作業控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job getEmployeeJob;
    private final BatchLogService batchLogService;

    /**
     * 手動執行 GET_EMPLOYEE_JOB 批次作業
     */
    @PostMapping("/jobs/get-employee/run")
    public ResponseEntity<String> runGetEmployeeJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(getEmployeeJob, jobParameters);

            return ResponseEntity.ok("批次作業已成功啟動");
        } catch (Exception e) {
            log.error("啟動批次作業失敗", e);
            return ResponseEntity.internalServerError()
                    .body("批次作業啟動失敗: " + e.getMessage());
        }
    }

    /**
     * 根據執行代號查詢日誌
     */
    @GetMapping("/logs/execution/{executionId}")
    public ResponseEntity<List<BatchLogEntity>> getLogsByExecutionId(@PathVariable String executionId) {
        try {
            List<BatchLogEntity> logs = batchLogService.getLogsByExecutionId(executionId);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("查詢日誌失敗", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根據作業名稱查詢日誌
     */
    @GetMapping("/logs/job/{jobName}")
    public ResponseEntity<List<BatchLogEntity>> getLogsByJobName(@PathVariable String jobName) {
        try {
            List<BatchLogEntity> logs = batchLogService.getLogsByJobName(jobName);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("查詢日誌失敗", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 查詢錯誤日誌
     */
    @GetMapping("/logs/errors/{executionId}")
    public ResponseEntity<List<BatchLogEntity>> getErrorLogs(@PathVariable String executionId) {
        try {
            List<BatchLogEntity> errorLogs = batchLogService.getErrorLogs(executionId);
            return ResponseEntity.ok(errorLogs);
        } catch (Exception e) {
            log.error("查詢錯誤日誌失敗", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根據時間範圍查詢日誌
     */
    @GetMapping("/logs/time-range")
    public ResponseEntity<List<BatchLogEntity>> getLogsByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            List<BatchLogEntity> logs = batchLogService.getLogsByTimeRange(start, end);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("查詢時間範圍日誌失敗", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 統計執行代號的日誌數量
     */
    @GetMapping("/logs/stats/{executionId}")
    public ResponseEntity<List<Object[]>> getLogStats(@PathVariable String executionId) {
        try {
            List<Object[]> stats = batchLogService.getLogCountByLevel(executionId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("查詢日誌統計失敗", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 清理舊日誌
     */
    @DeleteMapping("/logs/cleanup")
    public ResponseEntity<String> cleanupOldLogs(@RequestParam(defaultValue = "30") int retentionDays) {
        try {
            batchLogService.cleanOldLogs(retentionDays);
            return ResponseEntity.ok("舊日誌清理完成，保留天數: " + retentionDays);
        } catch (Exception e) {
            log.error("清理舊日誌失敗", e);
            return ResponseEntity.internalServerError()
                    .body("清理舊日誌失敗: " + e.getMessage());
        }
    }
}