package cub.ret.tru.batch.service;

import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.repository.BatchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批次日誌服務
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchLogService {

    private final BatchLogRepository batchLogRepository;

    /**
     * 保存日誌到資料庫
     * 使用新事務避免批次作業失敗時日誌無法保存
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(String executionId, String jobName, String stepName,
            String logLevel, String message, String loggerName,
            String threadName, String exceptionStack) {
        try {
            BatchLogEntity logEntity = BatchLogEntity.builder()
                    .executionId(executionId)
                    .jobName(jobName)
                    .stepName(stepName)
                    .logLevel(logLevel)
                    .message(message)
                    .loggerName(loggerName)
                    .threadName(threadName)
                    .exceptionStack(exceptionStack)
                    .logTime(LocalDateTime.now())
                    .build();

            batchLogRepository.save(logEntity);
        } catch (Exception e) {
            // 避免日誌保存失敗影響主要業務流程
            log.error("Failed to save batch log to database", e);
        }
    }

    /**
     * 保存日誌到資料庫（簡化版本）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(String executionId, String jobName, String logLevel, String message) {
        saveLog(executionId, jobName, null, logLevel, message, null, null, null);
    }

    /**
     * 根據執行代號查詢日誌
     */
    @Transactional(readOnly = true)
    public List<BatchLogEntity> getLogsByExecutionId(String executionId) {
        return batchLogRepository.findByExecutionIdOrderByLogTimeAsc(executionId);
    }

    /**
     * 根據作業名稱查詢日誌
     */
    @Transactional(readOnly = true)
    public List<BatchLogEntity> getLogsByJobName(String jobName) {
        return batchLogRepository.findByJobNameOrderByLogTimeDesc(jobName);
    }

    /**
     * 查詢指定執行代號的錯誤日誌
     */
    @Transactional(readOnly = true)
    public List<BatchLogEntity> getErrorLogs(String executionId) {
        return batchLogRepository.findErrorLogsByExecutionId(executionId);
    }

    /**
     * 根據時間範圍查詢日誌
     */
    @Transactional(readOnly = true)
    public List<BatchLogEntity> getLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return batchLogRepository.findByLogTimeBetween(startTime, endTime);
    }

    /**
     * 統計執行代號的日誌數量按級別分組
     */
    @Transactional(readOnly = true)
    public List<Object[]> getLogCountByLevel(String executionId) {
        return batchLogRepository.countLogsByLevelForExecution(executionId);
    }

    /**
     * 清理舊日誌（保留指定天數）
     */
    @Transactional
    public void cleanOldLogs(int retentionDays) {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            batchLogRepository.deleteOldLogs(cutoffDate);
            log.info("Successfully cleaned old logs before: {}", cutoffDate);
        } catch (Exception e) {
            log.error("Failed to clean old logs", e);
        }
    }
}