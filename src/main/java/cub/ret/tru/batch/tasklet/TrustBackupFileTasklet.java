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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * 檔案備份 Tasklet
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrustBackupFileTasklet implements Tasklet {

    private final BatchLogService batchLogService;
    private final Random random = new Random();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        String executionId = MDCUtil.getCurrentExecutionId();
        String jobName = MDCUtil.getCurrentJobName();

        log.debug("【DEBUG】進入 TrustBackupFileTasklet.execute()");
        batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】進入 TrustBackupFileTasklet.execute()");

        try {
            // 從上下文中取得檔案路徑
            String dataFilePath = (String) chunkContext.getStepContext()
                    .getStepExecution().getJobExecution().getExecutionContext()
                    .get("dataFilePath");
            String backupFilePath = (String) chunkContext.getStepContext()
                    .getStepExecution().getJobExecution().getExecutionContext()
                    .get("backupFilePath");
            String originalFileName = (String) chunkContext.getStepContext()
                    .getStepExecution().getJobExecution().getExecutionContext()
                    .get("originalFileName");

            log.debug("【DEBUG】取得參數 - 來源: {}, 備份目錄: {}, 檔案名: {}", 
                dataFilePath, backupFilePath, originalFileName);
            batchLogService.saveLog(executionId, jobName, "DEBUG", 
                String.format("【DEBUG】取得參數 - 來源: %s, 備份目錄: %s, 檔案名: %s", 
                    dataFilePath, backupFilePath, originalFileName));

            log.info("開始備份檔案");
            log.info("來源檔案: {}", dataFilePath);
            log.info("備份目錄: {}", backupFilePath);
            batchLogService.saveLog(executionId, jobName, "INFO",
                    "開始備份檔案 - 來源: " + dataFilePath + ", 目標: " + backupFilePath);

            // 隨機產生空間不足警告
            if (random.nextFloat() < 0.25f) { // 25% 機率
                String warnMsg = "【空間警告】備份目錄剩餘空間不足 20%，建議清理舊檔案";
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            // 檢查來源檔案是否存在
            Path sourcePath = Paths.get(dataFilePath);
            
            log.debug("【DEBUG】檢查來源檔案: {}", sourcePath.toString());
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】檢查來源檔案: " + sourcePath.toString());

            // 隨機產生檔案鎖定錯誤 (10% 機率)
            if (random.nextFloat() < 0.1f) {
                String errorMsg = "【隨機錯誤】來源檔案被其他程序鎖定，無法進行備份: " + dataFilePath;
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                throw new RuntimeException(errorMsg);
            }

            if (!Files.exists(sourcePath)) {
                String errorMsg = "來源檔案不存在: " + dataFilePath;
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                throw new RuntimeException(errorMsg);
            }

            // 獲取來源檔案資訊
            long sourceFileSize = Files.size(sourcePath);
            log.debug("【DEBUG】來源檔案大小: {} bytes", sourceFileSize);
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】來源檔案大小: " + sourceFileSize + " bytes");

            // 隨機產生檔案大小警告
            if (random.nextFloat() < 0.2f) { // 20% 機率
                String warnMsg = String.format("【檔案大小警告】檔案大小 %d bytes 超出建議範圍", sourceFileSize);
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            // 建立備份目錄（如果不存在）
            Path backupDirPath = Paths.get(backupFilePath);
            
            log.debug("【DEBUG】檢查備份目錄是否存在: {}", Files.exists(backupDirPath));
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】檢查備份目錄是否存在: " + Files.exists(backupDirPath));

            if (!Files.exists(backupDirPath)) {
                log.debug("【DEBUG】開始建立備份目錄");
                batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】開始建立備份目錄");

                Files.createDirectories(backupDirPath);
                log.info("建立備份目錄: {}", backupFilePath);
                batchLogService.saveLog(executionId, jobName, "INFO", "建立備份目錄: " + backupFilePath);
            }

            // 隨機產生權限警告
            if (random.nextFloat() < 0.15f) { // 15% 機率
                String warnMsg = "【權限警告】備份目錄可能存在權限限制，請確認寫入權限";
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            // 生成備份檔案名稱（加上時間戳和執行代號）
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            String backupFileName = getFileNameWithoutExtension(originalFileName) +
                    "_" + timestamp +
                    "_" + executionId +
                    getFileExtension(originalFileName);

            Path targetPath = backupDirPath.resolve(backupFileName);

            log.debug("【DEBUG】生成備份檔案名稱: {}", backupFileName);
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】生成備份檔案名稱: " + backupFileName);

            // 隨機產生備份衝突錯誤 (8% 機率)
            if (random.nextFloat() < 0.08f) {
                String errorMsg = "【隨機錯誤】備份檔案名稱衝突或目標路徑無效: " + targetPath.toString();
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                throw new RuntimeException(errorMsg);
            }

            log.debug("【DEBUG】開始執行檔案複製");
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】開始執行檔案複製");

            // 模擬複製過程的進度追蹤
            long copiedBytes = 0;
            int progressReports = 0;
            
            // 執行檔案備份
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.debug("【DEBUG】檔案複製完成，開始驗證");
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】檔案複製完成，開始驗證");

            // 驗證備份檔案
            long targetFileSize = Files.size(targetPath);
            
            log.debug("【DEBUG】備份檔案大小: {} bytes", targetFileSize);
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】備份檔案大小: " + targetFileSize + " bytes");

            // 驗證檔案大小是否一致
            if (sourceFileSize != targetFileSize) {
                String errorMsg = String.format("【驗證失敗】檔案大小不一致 - 來源: %d bytes, 備份: %d bytes", 
                    sourceFileSize, targetFileSize);
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                throw new RuntimeException(errorMsg);
            }

            // 隨機產生驗證警告
            if (random.nextFloat() < 0.12f) { // 12% 機率
                String warnMsg = "【驗證警告】建議執行檔案完整性檢查，確保備份品質";
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            log.info("檔案備份完成");
            log.info("備份檔案: {}", targetPath.toString());
            log.info("檔案大小: {} bytes", targetFileSize);
            log.info("備份驗證: 通過");

            batchLogService.saveLog(executionId, jobName, "INFO", "檔案備份完成");
            batchLogService.saveLog(executionId, jobName, "INFO", "備份檔案: " + targetPath.toString());
            batchLogService.saveLog(executionId, jobName, "INFO", "檔案大小: " + targetFileSize + " bytes");
            batchLogService.saveLog(executionId, jobName, "INFO", "備份驗證: 通過");

            // 隨機產生清理建議
            if (random.nextFloat() < 0.3f) { // 30% 機率
                String infoMsg = "【建議】備份完成後可考慮清理 7 天前的舊備份檔案";
                log.info(infoMsg);
                batchLogService.saveLog(executionId, jobName, "INFO", infoMsg);
            }

            log.debug("【DEBUG】TrustBackupFileTasklet.execute() 正常結束");
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】TrustBackupFileTasklet.execute() 正常結束");

            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            log.error("檔案備份失敗", e);
            batchLogService.saveLog(executionId, jobName, "ERROR",
                    "檔案備份失敗: " + e.getMessage());

            log.debug("【DEBUG】TrustBackupFileTasklet.execute() 異常結束: {}", e.getClass().getSimpleName());
            batchLogService.saveLog(executionId, jobName, "DEBUG", 
                "【DEBUG】TrustBackupFileTasklet.execute() 異常結束: " + e.getClass().getSimpleName());
            
            throw e;
        }
    }

    /**
     * 取得檔案名稱（不含副檔名）
     */
    private String getFileNameWithoutExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }

    /**
     * 取得檔案副檔名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex);
        }
        return "";
    }
}