package cub.ret.tru.batch.job;

import cub.ret.tru.batch.listener.base.BaseJobListener;
import cub.ret.tru.batch.service.BatchLogService;
import cub.ret.tru.batch.tasklet.GetEmployeeJobTasklet;
import cub.ret.tru.batch.tasklet.TrustBackupFileTasklet;
import cub.ret.tru.batch.util.MDCUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;

/**
 * GET_EMPLOYEE_JOB 個法人信託行員檔拆解與寫入
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GetEmployeeJobConfig {

    /** JobRepository */
    private final JobRepository jobRepository;

    /** PlatformTransactionManager */
    private final PlatformTransactionManager transactionManager;

    /** GetEmployeeJobTasklet */
    private final GetEmployeeJobTasklet getEmployeeJobTasklet;

    /** TrustBackupFileTasklet */
    private final TrustBackupFileTasklet trustBackupFileTasklet;

    /** BaseJobListener */
    private final BaseJobListener baseJobListener;

    /** BatchLogService */
    private final BatchLogService batchLogService;

    /** 檔案來源路徑 */
    @Value("${filePath:/naspool/ftppool}")
    private String filePath;

    /** 檔案備份路徑 */
    @Value("${backupPath:/naspool/ftppool/Backup}")
    private String backupPath;

    /** 行員檔案名稱 */
    private static final String EMPLOYEE_FILE_NAME = "BOND_MEMMARK.txt";

    @Bean
    public Job getEmployeeJob() {
        return new JobBuilder("GET_EMPLOYEE_JOB", jobRepository)
                .listener(baseJobListener)
                .start(initializeEmployeeJobContextStep())
                .next(processEmployeeFileStep())
                .next(backupEmployeeFileStep())
                .build();
    }

    @Bean
    public Step initializeEmployeeJobContextStep() {
        return new StepBuilder("initializeEmployeeJobContextStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String executionId = MDCUtil.getCurrentExecutionId();
                    String jobName = MDCUtil.getCurrentJobName();
                    String stepName = "initializeEmployeeJobContextStep";

                    MDCUtil.setCurrentStepName(stepName);

                    log.info("STEP 1: 初始化 GET_EMPLOYEE_JOB 作業上下文");
                    batchLogService.saveLog(executionId, jobName, "INFO", "STEP 1: 初始化作業上下文開始");

                    // 設定原始檔案路徑（不帶日期）
                    String dataFilePath = filePath + File.separator + EMPLOYEE_FILE_NAME;
                    chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                            .putString("dataFilePath", dataFilePath);
                    chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                            .putString("backupFilePath", backupPath);
                    chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                            .putString("originalFileName", EMPLOYEE_FILE_NAME);

                    // 記錄設定的參數
                    log.info("資料檔案路徑: {}", dataFilePath);
                    log.info("備份檔案路徑: {}", backupPath);
                    log.info("原始檔案名稱: {}", EMPLOYEE_FILE_NAME);
                    log.info("執行代號: {}", executionId);

                    batchLogService.saveLog(executionId, jobName, "INFO",
                            String.format("參數設定完成 - 資料路徑: %s, 備份路徑: %s", dataFilePath, backupPath));

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step processEmployeeFileStep() {
        return new StepBuilder("processEmployeeFileStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String executionId = MDCUtil.getCurrentExecutionId();
                    String jobName = MDCUtil.getCurrentJobName();
                    String stepName = "processEmployeeFileStep";

                    MDCUtil.setCurrentStepName(stepName);

                    log.info("STEP 2: 開始處理行員檔案");
                    batchLogService.saveLog(executionId, jobName, "INFO", "STEP 2: 開始處理行員檔案");

                    try {
                        // 執行實際的檔案處理邏輯
                        RepeatStatus result = getEmployeeJobTasklet.execute(contribution, chunkContext);

                        batchLogService.saveLog(executionId, jobName, "INFO", "行員檔案處理完成");
                        return result;
                    } catch (Exception e) {
                        log.error("行員檔案處理失敗", e);
                        batchLogService.saveLog(executionId, jobName, "ERROR",
                                "行員檔案處理失敗: " + e.getMessage());
                        throw e;
                    }
                }, transactionManager)
                .build();
    }

    @Bean
    public Step backupEmployeeFileStep() {
        return new StepBuilder("backupEmployeeFileStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String executionId = MDCUtil.getCurrentExecutionId();
                    String jobName = MDCUtil.getCurrentJobName();
                    String stepName = "backupEmployeeFileStep";

                    MDCUtil.setCurrentStepName(stepName);

                    log.info("STEP 3: 開始備份檔案");
                    batchLogService.saveLog(executionId, jobName, "INFO", "STEP 3: 開始備份檔案");

                    try {
                        // 執行實際的檔案備份邏輯
                        RepeatStatus result = trustBackupFileTasklet.execute(contribution, chunkContext);

                        batchLogService.saveLog(executionId, jobName, "INFO", "檔案備份完成");
                        return result;
                    } catch (Exception e) {
                        log.error("檔案備份失敗", e);
                        batchLogService.saveLog(executionId, jobName, "ERROR",
                                "檔案備份失敗: " + e.getMessage());
                        throw e;
                    }
                }, transactionManager)
                .build();
    }
}