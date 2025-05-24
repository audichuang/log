package cub.ret.tru.batch.job;

import cub.ret.tru.batch.listener.base.BaseJobListener;
import cub.ret.tru.batch.service.BatchLogService;
import cub.ret.tru.batch.tasklet.DataValidationTasklet;
import cub.ret.tru.batch.tasklet.GetEmployeeJobTasklet;
import cub.ret.tru.batch.tasklet.TrustBackupFileTasklet;
import cub.ret.tru.batch.util.MDCUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Random;

/**
 * GET_EMPLOYEE_JOB 個法人信託行員檔拆解與寫入
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GetEmployeeJobConfig {

    /**
     * JobRepository
     */
    private final JobRepository jobRepository;

    /**
     * PlatformTransactionManager
     */
    private final PlatformTransactionManager transactionManager;

    /**
     * GetEmployeeJobTasklet
     */
    private final GetEmployeeJobTasklet getEmployeeJobTasklet;

    /**
     * TrustBackupFileTasklet
     */
    private final TrustBackupFileTasklet trustBackupFileTasklet;

    /**
     * DataValidationTasklet
     */
    private final DataValidationTasklet dataValidationTasklet;

    /**
     * BaseJobListener
     */
    private final BaseJobListener baseJobListener;

    /**
     * BatchLogService
     */
    private final BatchLogService batchLogService;

    /**
     * 檔案來源路徑
     */
    @Value("${filePath:/naspool/ftppool}")
    private String filePath;

    /**
     * 檔案備份路徑
     */
    @Value("${backupPath:/naspool/ftppool/Backup}")
    private String backupPath;

    /**
     * 行員檔案名稱
     */
    private static final String EMPLOYEE_FILE_NAME = "BOND_MEMMARK.txt";

    /**
     * 隨機數生成器
     */
    private final Random random = new Random();

    @Bean("GET_EMPLOYEE_JOB")
    public Job getEmployeeJob() {
        return new JobBuilder("GET_EMPLOYEE_JOB", jobRepository)
                .listener(baseJobListener)
                .start(initializeEmployeeJobContextStep())
                .next(processEmployeeFileStep())
                .next(dataValidationStep())
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

                    log.debug("【DEBUG】進入初始化步驟");
                    batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】進入初始化步驟");

                    log.info("STEP 1: 初始化 GET_EMPLOYEE_JOB 作業上下文");
                    batchLogService.saveLog(executionId, jobName, "INFO", "STEP 1: 初始化作業上下文開始");

                    // 隨機產生配置警告
                    if (random.nextFloat() < 0.2f) { // 20% 機率
                        String warnMsg = "【配置警告】檢測到系統負載較高，建議在離峰時間執行";
                        log.warn(warnMsg);
                        batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
                    }

                    log.debug("【DEBUG】讀取配置參數 - filePath: {}, backupPath: {}", filePath, backupPath);
                    batchLogService.saveLog(executionId, jobName, "DEBUG",
                            String.format("【DEBUG】讀取配置參數 - filePath: %s, backupPath: %s", filePath, backupPath));

                    // 隨機產生配置錯誤 (5% 機率)
                    if (random.nextFloat() < 0.05f) {
                        String errorMsg = "【隨機錯誤】配置檔案路徑無效或無權限存取";
                        log.error(errorMsg);
                        batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                        throw new RuntimeException(errorMsg);
                    }

                    // 設定原始檔案路徑（不帶日期）
                    String dataFilePath = filePath + File.separator + EMPLOYEE_FILE_NAME;

                    log.debug("【DEBUG】組合檔案路徑: {}", dataFilePath);
                    batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】組合檔案路徑: " + dataFilePath);

                    chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                            .putString("dataFilePath", dataFilePath);
                    chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                            .putString("backupFilePath", backupPath);
                    chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                            .putString("originalFileName", EMPLOYEE_FILE_NAME);

                    log.debug("【DEBUG】參數設定到執行上下文完成");
                    batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】參數設定到執行上下文完成");

                    // 記錄設定的參數
                    log.info("資料檔案路徑: {}", dataFilePath);
                    log.info("備份檔案路徑: {}", backupPath);
                    log.info("原始檔案名稱: {}", EMPLOYEE_FILE_NAME);
                    log.info("執行代號: {}", executionId);

                    batchLogService.saveLog(executionId, jobName, "INFO",
                            String.format("參數設定完成 - 資料路徑: %s, 備份路徑: %s", dataFilePath, backupPath));

                    // 隨機產生初始化警告
                    if (random.nextFloat() < 0.15f) { // 15% 機率
                        String warnMsg = "【初始化警告】建議檢查磁碟空間是否充足";
                        log.warn(warnMsg);
                        batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
                    }

                    log.debug("【DEBUG】初始化步驟完成");
                    batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】初始化步驟完成");

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

                    log.debug("【DEBUG】進入檔案處理步驟");
                    batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】進入檔案處理步驟");

                    log.info("STEP 2: 開始處理行員檔案");
                    batchLogService.saveLog(executionId, jobName, "INFO", "STEP 2: 開始處理行員檔案");

                    // 隨機產生步驟間錯誤 (8% 機率)
                    if (random.nextFloat() < 0.08f) {
                        String errorMsg = "【隨機錯誤】步驟間狀態傳遞失敗，無法繼續處理";
                        log.error(errorMsg);
                        batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                        throw new RuntimeException(errorMsg);
                    }

                    // 隨機產生內存警告
                    if (random.nextFloat() < 0.25f) { // 25% 機率
                        String warnMsg = "【內存警告】當前內存使用率較高，處理速度可能受影響";
                        log.warn(warnMsg);
                        batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
                    }

                    log.debug("【DEBUG】準備調用 GetEmployeeJobTasklet");
                    batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】準備調用 GetEmployeeJobTasklet");

                    try {
                        // 執行實際的檔案處理邏輯
                        RepeatStatus result = getEmployeeJobTasklet.execute(contribution, chunkContext);

                        log.debug("【DEBUG】GetEmployeeJobTasklet 執行完成，結果: {}", result);
                        batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】GetEmployeeJobTasklet 執行完成，結果: " + result);

                        batchLogService.saveLog(executionId, jobName, "INFO", "STEP 2: 行員檔案處理完成");
                        return result;
                    } catch (Exception e) {
                        log.error("STEP 2: 行員檔案處理失敗", e);
                        batchLogService.saveLog(executionId, jobName, "ERROR",
                                "STEP 2: 行員檔案處理失敗: " + e.getMessage());

                        log.debug("【DEBUG】檔案處理步驟異常結束: {}", e.getClass().getSimpleName());
                        batchLogService.saveLog(executionId, jobName, "DEBUG",
                                "【DEBUG】檔案處理步驟異常結束: " + e.getClass().getSimpleName());

                        throw e;
                    }
                }, transactionManager)
                .build();
    }

    @Bean
    public Step dataValidationStep() {
        return new StepBuilder("dataValidationStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String executionId = MDCUtil.getCurrentExecutionId();
                    String jobName = MDCUtil.getCurrentJobName();
                    String stepName = "dataValidationStep";

                    MDCUtil.setCurrentStepName(stepName);

                    log.debug("【DEBUG】進入資料驗證步驟");
                    batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】進入資料驗證步驟");

                    log.info("STEP 3: 開始資料驗證");
                    batchLogService.saveLog(executionId, jobName, "INFO", "STEP 3: 開始資料驗證");

                    // 隨機產生驗證前置警告
                    if (random.nextFloat() < 0.2f) { // 20% 機率
                        String warnMsg = "【驗證警告】檢測到大量資料，驗證時間可能較長";
                        log.warn(warnMsg);
                        batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
                    }

                    // 隨機產生驗證前置錯誤 (7% 機率)
                    if (random.nextFloat() < 0.07f) {
                        String errorMsg = "【隨機錯誤】資料驗證服務暫時不可用";
                        log.error(errorMsg);
                        batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                        throw new RuntimeException(errorMsg);
                    }

                    log.debug("【DEBUG】準備調用 DataValidationTasklet");
                    batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】準備調用 DataValidationTasklet");

                    try {
                        // 執行實際的資料驗證邏輯
                        RepeatStatus result = dataValidationTasklet.execute(contribution, chunkContext);

                        log.debug("【DEBUG】DataValidationTasklet 執行完成，結果: {}", result);
                        batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】DataValidationTasklet 執行完成，結果: " + result);

                        batchLogService.saveLog(executionId, jobName, "INFO", "STEP 3: 資料驗證完成");
                        return result;
                    } catch (Exception e) {
                        log.error("STEP 3: 資料驗證失敗", e);
                        batchLogService.saveLog(executionId, jobName, "ERROR",
                                "STEP 3: 資料驗證失敗: " + e.getMessage());

                        log.debug("【DEBUG】資料驗證步驟異常結束: {}", e.getClass().getSimpleName());
                        batchLogService.saveLog(executionId, jobName, "DEBUG",
                                "【DEBUG】資料驗證步驟異常結束: " + e.getClass().getSimpleName());

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

                    log.debug("【DEBUG】進入檔案備份步驟");
                    batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】進入檔案備份步驟");

                    log.info("STEP 4: 開始備份檔案");
                    batchLogService.saveLog(executionId, jobName, "INFO", "STEP 4: 開始備份檔案");

                    // 隨機產生網絡警告
                    if (random.nextFloat() < 0.18f) { // 18% 機率
                        String warnMsg = "【網絡警告】檢測到網絡延遲較高，備份時間可能延長";
                        log.warn(warnMsg);
                        batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
                    }

                    // 隨機產生備份前置錯誤 (6% 機率)
                    if (random.nextFloat() < 0.06f) {
                        String errorMsg = "【隨機錯誤】備份服務暫時不可用，請稍後重試";
                        log.error(errorMsg);
                        batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                        throw new RuntimeException(errorMsg);
                    }

                    log.debug("【DEBUG】準備調用 TrustBackupFileTasklet");
                    batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】準備調用 TrustBackupFileTasklet");

                    try {
                        // 執行實際的檔案備份邏輯
                        RepeatStatus result = trustBackupFileTasklet.execute(contribution, chunkContext);

                        log.debug("【DEBUG】TrustBackupFileTasklet 執行完成，結果: {}", result);
                        batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】TrustBackupFileTasklet 執行完成，結果: " + result);

                        batchLogService.saveLog(executionId, jobName, "INFO", "STEP 4: 檔案備份完成");

                        // 隨機產生清理建議
                        if (random.nextFloat() < 0.4f) { // 40% 機率
                            String infoMsg = "【建議】作業執行完成，建議定期檢查日誌檔案大小";
                            log.info(infoMsg);
                            batchLogService.saveLog(executionId, jobName, "INFO", infoMsg);
                        }

                        return result;
                    } catch (Exception e) {
                        log.error("STEP 4: 檔案備份失敗", e);
                        batchLogService.saveLog(executionId, jobName, "ERROR",
                                "STEP 4: 檔案備份失敗: " + e.getMessage());

                        log.debug("【DEBUG】檔案備份步驟異常結束: {}", e.getClass().getSimpleName());
                        batchLogService.saveLog(executionId, jobName, "DEBUG",
                                "【DEBUG】檔案備份步驟異常結束: " + e.getClass().getSimpleName());

                        throw e;
                    }
                }, transactionManager)
                .build();
    }
}