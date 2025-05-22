package cub.ret.tru.batch.listener.base;

import cub.ret.tru.batch.service.BatchLogService;
import cub.ret.tru.batch.util.MDCUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 基礎作業監聽器
 * 負責生成執行代號、設定執行上下文以及記錄作業執行日誌
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaseJobListener implements JobExecutionListener {

    private final BatchLogService batchLogService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    @Override
    public void beforeJob(JobExecution jobExecution) {
        try {
            // 生成執行代號：時間戳 + 作業名稱 (格式: yyyyMMddHHmmJOB_NAME)
            String timestamp = LocalDateTime.now().format(FORMATTER);
            String jobName = jobExecution.getJobInstance().getJobName();
            String executionId = timestamp + jobName;

            // 設定 MDC 上下文 (支援同步和異步日誌)
            MDCUtil.setJobContext(executionId, jobExecution.getJobInstance().getJobName());

            // 將執行代號存入JobExecution上下文中
            jobExecution.getExecutionContext().putString("executionId", executionId);

            // 記錄作業開始日誌
            log.info("=== 作業開始執行 ===");
            log.info("執行代號: {}", executionId);
            log.info("作業名稱: {}", jobExecution.getJobInstance().getJobName());
            log.info("作業實例ID: {}", jobExecution.getJobInstance().getId());
            log.info("作業執行ID: {}", jobExecution.getId());
            log.info("開始時間: {}", jobExecution.getStartTime());

            // 保存開始日誌到資料庫
            batchLogService.saveLog(executionId, jobExecution.getJobInstance().getJobName(),
                    "INFO", "作業開始執行");

        } catch (Exception e) {
            log.error("Failed to initialize job execution context", e);
            throw new RuntimeException("作業初始化失敗", e);
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String executionId = MDCUtil.getCurrentExecutionId();
        String jobName = MDCUtil.getCurrentJobName();

        try {
            // 記錄作業結束日誌
            log.info("=== 作業執行完成 ===");
            log.info("執行代號: {}", executionId);
            log.info("作業名稱: {}", jobName);
            log.info("執行狀態: {}", jobExecution.getStatus());
            log.info("結束時間: {}", jobExecution.getEndTime());

            // 執行時長信息（由於API版本問題，暫時註釋）
            // TODO: 根據實際Spring Batch版本調整時長計算方式

            // 記錄退出狀態
            if (jobExecution.getExitStatus().getExitCode().equals("COMPLETED")) {
                log.info("作業執行成功完成");
                batchLogService.saveLog(executionId, jobName, "INFO", "作業執行成功完成");
            } else {
                log.warn("作業執行異常結束，退出代碼: {}", jobExecution.getExitStatus().getExitCode());
                batchLogService.saveLog(executionId, jobName, "WARN",
                        "作業執行異常結束，退出代碼: " + jobExecution.getExitStatus().getExitCode());
            }

            // 記錄錯誤資訊（如果有）
            if (!jobExecution.getAllFailureExceptions().isEmpty()) {
                for (Throwable throwable : jobExecution.getAllFailureExceptions()) {
                    log.error("作業執行過程中發生錯誤: {}", throwable.getMessage(), throwable);
                    batchLogService.saveLog(executionId, jobName, "ERROR",
                            "作業執行錯誤: " + throwable.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Failed to finalize job execution", e);
        } finally {
            // 清除 MDC 上下文
            MDCUtil.clear();
        }
    }
}