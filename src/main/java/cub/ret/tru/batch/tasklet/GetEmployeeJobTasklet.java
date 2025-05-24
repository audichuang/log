package cub.ret.tru.batch.tasklet;

import cub.ret.tru.batch.service.BatchLogService;
import cub.ret.tru.batch.util.MDCUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

/**
 * 行員檔案處理 Tasklet
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetEmployeeJobTasklet implements Tasklet {

    private final BatchLogService batchLogService;
    private final Random random = new Random();

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        String executionId = MDCUtil.getCurrentExecutionId();
        String jobName = MDCUtil.getCurrentJobName();

        log.debug("【DEBUG】進入 GetEmployeeJobTasklet.execute()");
        batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】進入 GetEmployeeJobTasklet.execute()");

        try {
            // 從上下文中取得檔案路徑
            String dataFilePath = (String) chunkContext.getStepContext()
                    .getStepExecution().getJobExecution().getExecutionContext()
                    .get("dataFilePath");

            log.debug("【DEBUG】取得檔案路徑參數: {}", dataFilePath);
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】取得檔案路徑參數: " + dataFilePath);

            log.info("開始處理行員檔案: {}", dataFilePath);
            batchLogService.saveLog(executionId, jobName, "INFO", "開始處理行員檔案: " + dataFilePath);

            // 隨機產生警告訊息
            if (random.nextFloat() < 0.3f) { // 30% 機率
                String warnMsg = "【警告】檔案路徑包含特殊字元，可能影響處理效能";
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            // 檢查檔案是否存在
            File dataFile = new File(dataFilePath);
            
            log.debug("【DEBUG】檢查檔案是否存在: {}", dataFile.exists());
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】檢查檔案是否存在: " + dataFile.exists());

            // 隨機產生檔案不存在錯誤 (15% 機率)
            if (random.nextFloat() < 0.15f) {
                String errorMsg = "【隨機錯誤】行員檔案不存在或無法讀取: " + dataFilePath;
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                throw new RuntimeException(errorMsg);
            }

            if (!dataFile.exists()) {
                String errorMsg = "行員檔案不存在: " + dataFilePath;
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                throw new RuntimeException(errorMsg);
            }

            log.debug("【DEBUG】開始讀取檔案行數");
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】開始讀取檔案行數");

            // 讀取檔案行數
            long lineCount = Files.lines(Paths.get(dataFilePath)).count();
            log.info("檔案總行數: {}", lineCount);
            batchLogService.saveLog(executionId, jobName, "INFO", "檔案總行數: " + lineCount);

            // 隨機產生資料品質警告
            if (random.nextFloat() < 0.4f) { // 40% 機率
                String warnMsg = String.format("【資料品質警告】檔案行數 %d 超出預期範圍，建議檢查資料完整性", lineCount);
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            // 模擬檔案處理邏輯
            log.info("正在解析行員檔案...");
            batchLogService.saveLog(executionId, jobName, "INFO", "正在解析行員檔案...");

            // 模擬處理過程中的debug追蹤
            int totalRecords = (int) lineCount;
            int processedRecords = 0;
            int errorRecords = 0;
            int batchSize = 100;

            for (int i = 0; i < totalRecords; i += batchSize) {
                int currentBatch = Math.min(batchSize, totalRecords - i);
                
                log.debug("【DEBUG】處理批次 {}-{}, 批次大小: {}", i + 1, i + currentBatch, currentBatch);
                batchLogService.saveLog(executionId, jobName, "DEBUG", 
                    String.format("【DEBUG】處理批次 %d-%d, 批次大小: %d", i + 1, i + currentBatch, currentBatch));

                // 模擬處理時間
                Thread.sleep(100);

                // 隨機產生處理錯誤 (10% 機率)
                if (random.nextFloat() < 0.1f) {
                    errorRecords += random.nextInt(3) + 1;
                    String warnMsg = String.format("【資料錯誤】批次 %d-%d 發現 %d 筆格式錯誤的記錄", 
                        i + 1, i + currentBatch, errorRecords);
                    log.warn(warnMsg);
                    batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
                }

                // 隨機產生嚴重錯誤 (5% 機率)
                if (random.nextFloat() < 0.05f) {
                    String errorMsg = String.format("【處理錯誤】批次 %d-%d 發生資料庫連線錯誤", i + 1, i + currentBatch);
                    log.error(errorMsg);
                    batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                    // 不拋出異常，繼續處理
                }

                processedRecords += currentBatch;

                // 定期報告進度
                if ((i + currentBatch) % 500 == 0 || (i + currentBatch) == totalRecords) {
                    double progress = ((double) processedRecords / totalRecords) * 100;
                    log.info("處理進度: {}/{} ({:.1f}%), 錯誤記錄: {}", 
                        processedRecords, totalRecords, progress, errorRecords);
                    batchLogService.saveLog(executionId, jobName, "INFO", 
                        String.format("處理進度: %d/%d (%.1f%%), 錯誤記錄: %d", 
                            processedRecords, totalRecords, progress, errorRecords));
                }
            }

            log.debug("【DEBUG】檔案處理邏輯完成，開始統計");
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】檔案處理邏輯完成，開始統計");

            // 處理完成，記錄統計資訊
            log.info("行員檔案處理完成");
            log.info("總處理記錄數: {}", processedRecords);
            log.info("錯誤記錄數: {}", errorRecords);
            log.info("成功處理率: {:.2f}%", (double)(processedRecords - errorRecords) / processedRecords * 100);

            batchLogService.saveLog(executionId, jobName, "INFO", "行員檔案處理完成");
            batchLogService.saveLog(executionId, jobName, "INFO", "總處理記錄數: " + processedRecords);
            batchLogService.saveLog(executionId, jobName, "INFO", "錯誤記錄數: " + errorRecords);
            batchLogService.saveLog(executionId, jobName, "INFO", 
                String.format("成功處理率: %.2f%%", (double)(processedRecords - errorRecords) / processedRecords * 100));

            // 隨機產生最終警告
            if (errorRecords > 0) {
                String warnMsg = String.format("【處理完成警告】發現 %d 筆錯誤記錄，請檢查錯誤日誌", errorRecords);
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            log.debug("【DEBUG】GetEmployeeJobTasklet.execute() 正常結束");
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】GetEmployeeJobTasklet.execute() 正常結束");

            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            log.error("行員檔案處理失敗", e);
            batchLogService.saveLog(executionId, jobName, "ERROR",
                    "行員檔案處理失敗: " + e.getMessage());
            
            log.debug("【DEBUG】GetEmployeeJobTasklet.execute() 異常結束: {}", e.getClass().getSimpleName());
            batchLogService.saveLog(executionId, jobName, "DEBUG", 
                "【DEBUG】GetEmployeeJobTasklet.execute() 異常結束: " + e.getClass().getSimpleName());
            
            throw e;
        }
    }
}