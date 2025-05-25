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
}