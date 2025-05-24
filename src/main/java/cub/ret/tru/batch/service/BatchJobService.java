package cub.ret.tru.batch.service;

import cub.ret.tru.batch.entity.BatchExecutionEntity;
import cub.ret.tru.batch.entity.BatchJobEntity;
import cub.ret.tru.batch.repository.BatchExecutionRepository;
import cub.ret.tru.batch.repository.BatchJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 批次作業服務
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchJobService {

    private final BatchJobRepository batchJobRepository;
    private final BatchExecutionRepository batchExecutionRepository;
    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    /**
     * 獲取所有批次作業
     */
    public List<BatchJobEntity> getAllJobs() {
        return batchJobRepository.findByEnabledTrueOrderByUpdatedAtDesc();
    }

    /**
     * 根據 ID 查詢作業
     */
    public BatchJobEntity getJobById(String jobId) {
        return batchJobRepository.findById(jobId).orElse(null);
    }

    /**
     * 執行批次作業
     */
    public BatchExecutionEntity runJob(String jobId) throws Exception {
        // 查詢作業信息
        BatchJobEntity jobEntity = batchJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("找不到作業: " + jobId));

        if (!jobEntity.getEnabled()) {
            throw new IllegalStateException("作業已停用: " + jobId);
        }

        // 檢查是否已有執行中的實例
        List<BatchExecutionEntity> runningExecutions = batchExecutionRepository.findByStatusIn(
                List.of(BatchExecutionEntity.ExecutionStatus.RUNNING)
        );
        
        boolean isAlreadyRunning = runningExecutions.stream()
                .anyMatch(execution -> execution.getJobName().equals(jobEntity.getName()));
        
        if (isAlreadyRunning) {
            throw new IllegalStateException("作業正在執行中: " + jobId);
        }

        // 更新作業狀態為執行中
        jobEntity.setStatus(BatchJobEntity.JobStatus.RUNNING);
        jobEntity.setLastExecutionTime(LocalDateTime.now());
        batchJobRepository.save(jobEntity);

        try {
            // 獲取 Spring Batch Job
            Job job = applicationContext.getBean(jobEntity.getName(), Job.class);

            // 設置作業參數 - 讓 BaseJobListener 生成執行代號
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            // 執行作業
            JobExecution jobExecution = jobLauncher.run(job, jobParameters);

            // 執行完成後，取得執行代號（由 BaseJobListener 生成）
            String executionId = jobExecution.getExecutionContext().getString("executionId");
            
            // 創建執行記錄
            LocalDateTime now = LocalDateTime.now();
            BatchExecutionEntity execution = BatchExecutionEntity.builder()
                    .executionId(executionId)
                    .jobName(jobEntity.getName())
                    .status(BatchExecutionEntity.ExecutionStatus.COMPLETED)
                    .startTime(now)
                    .endTime(now)
                    .message("批次作業執行完成")
                    .createdAt(now)  // 手動設置創建時間
                    .updatedAt(now)  // 手動設置更新時間
                    .build();
            
            batchExecutionRepository.save(execution);

            // 更新作業狀態
            jobEntity.setStatus(BatchJobEntity.JobStatus.COMPLETED);
            jobEntity.setLastExecutionId(executionId);
            batchJobRepository.save(jobEntity);

            log.info("批次作業已完成: {} (執行代號: {})", jobEntity.getDisplayName(), executionId);
            return execution;

        } catch (JobExecutionAlreadyRunningException | JobRestartException | 
                 JobInstanceAlreadyCompleteException | JobParametersInvalidException e) {
            
            // 更新作業狀態為失敗
            jobEntity.setStatus(BatchJobEntity.JobStatus.FAILED);
            batchJobRepository.save(jobEntity);

            log.error("執行批次作業失敗: {}", e.getMessage(), e);
            throw new Exception("執行批次作業失敗: " + e.getMessage(), e);
        }
    }

    /**
     * 停止批次作業
     */
    @Transactional
    public void stopJob(String executionId) {
        BatchExecutionEntity execution = batchExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("找不到執行記錄: " + executionId));

        if (execution.getStatus() != BatchExecutionEntity.ExecutionStatus.RUNNING) {
            throw new IllegalStateException("作業不在執行中");
        }

        // 更新執行狀態
        execution.setStatus(BatchExecutionEntity.ExecutionStatus.STOPPED);
        execution.setEndTime(LocalDateTime.now());
        execution.setMessage("作業已手動停止");
        batchExecutionRepository.save(execution);

        // 更新作業狀態
        BatchJobEntity jobEntity = batchJobRepository.findByName(execution.getJobName())
                .orElse(null);
        if (jobEntity != null) {
            jobEntity.setStatus(BatchJobEntity.JobStatus.IDLE);
            batchJobRepository.save(jobEntity);
        }

        log.info("批次作業已停止: {} (執行代號: {})", execution.getJobName(), executionId);
    }

    /**
     * 查詢執行記錄
     */
    public BatchExecutionEntity getExecution(String executionId) {
        return batchExecutionRepository.findById(executionId).orElse(null);
    }

    /**
     * 查詢作業的執行歷史
     */
    public List<BatchExecutionEntity> getJobExecutions(String jobName) {
        return batchExecutionRepository.findByJobNameOrderByStartTimeDesc(jobName);
    }

    /**
     * 初始化作業數據 - 創建實際的 GET_EMPLOYEE_JOB
     */
    @Transactional
    public void initializeJobs() {
        // 檢查是否已有 GET_EMPLOYEE_JOB
        if (batchJobRepository.findById("get-employee-job").isPresent()) {
            log.info("GET_EMPLOYEE_JOB 已存在，跳過初始化");
            return;
        }

        log.info("創建 GET_EMPLOYEE_JOB 批次作業...");

        // 創建實際存在的批次作業
        BatchJobEntity getEmployeeJob = BatchJobEntity.builder()
                .id("get-employee-job")
                .name("getEmployeeJob")  // 對應到實際的 Spring Bean 名稱
                .displayName("GET_EMPLOYEE_JOB")
                .description("個法人信託行員檔拆解與寫入")
                .status(BatchJobEntity.JobStatus.IDLE)
                .isScheduled(false)
                .enabled(true)
                .category("資料處理")
                .estimatedDuration(10)
                .build();

        batchJobRepository.save(getEmployeeJob);
        log.info("已成功創建批次作業: {} (Bean名稱: {})", getEmployeeJob.getDisplayName(), getEmployeeJob.getName());
    }

    /**
     * 生成執行代號
     */
    private String generateExecutionId() {
        return "exec-" + UUID.randomUUID().toString().substring(0, 8);
    }
} 