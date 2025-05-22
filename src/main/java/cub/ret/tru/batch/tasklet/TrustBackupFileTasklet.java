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

/**
 * 檔案備份 Tasklet
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrustBackupFileTasklet implements Tasklet {

    private final BatchLogService batchLogService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        String executionId = MDCUtil.getCurrentExecutionId();
        String jobName = MDCUtil.getCurrentJobName();

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

            log.info("開始備份檔案");
            log.info("來源檔案: {}", dataFilePath);
            log.info("備份目錄: {}", backupFilePath);
            batchLogService.saveLog(executionId, jobName, "INFO",
                    "開始備份檔案 - 來源: " + dataFilePath + ", 目標: " + backupFilePath);

            // 檢查來源檔案是否存在
            Path sourcePath = Paths.get(dataFilePath);
            if (!Files.exists(sourcePath)) {
                String errorMsg = "來源檔案不存在: " + dataFilePath;
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                throw new RuntimeException(errorMsg);
            }

            // 建立備份目錄（如果不存在）
            Path backupDirPath = Paths.get(backupFilePath);
            if (!Files.exists(backupDirPath)) {
                Files.createDirectories(backupDirPath);
                log.info("建立備份目錄: {}", backupFilePath);
                batchLogService.saveLog(executionId, jobName, "INFO", "建立備份目錄: " + backupFilePath);
            }

            // 生成備份檔案名稱（加上時間戳和執行代號）
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            String backupFileName = getFileNameWithoutExtension(originalFileName) +
                    "_" + timestamp +
                    "_" + executionId +
                    getFileExtension(originalFileName);

            Path targetPath = backupDirPath.resolve(backupFileName);

            // 執行檔案備份
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            long fileSize = Files.size(targetPath);
            log.info("檔案備份完成");
            log.info("備份檔案: {}", targetPath.toString());
            log.info("檔案大小: {} bytes", fileSize);

            batchLogService.saveLog(executionId, jobName, "INFO",
                    String.format("檔案備份完成 - 檔案: %s, 大小: %d bytes",
                            targetPath.toString(), fileSize));

            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            log.error("檔案備份失敗", e);
            batchLogService.saveLog(executionId, jobName, "ERROR",
                    "檔案備份失敗: " + e.getMessage());
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