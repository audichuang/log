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

/**
 * 行員檔案處理 Tasklet
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetEmployeeJobTasklet implements Tasklet {

    private final BatchLogService batchLogService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        String executionId = MDCUtil.getCurrentExecutionId();
        String jobName = MDCUtil.getCurrentJobName();

        try {
            // 從上下文中取得檔案路徑
            String dataFilePath = (String) chunkContext.getStepContext()
                    .getStepExecution().getJobExecution().getExecutionContext()
                    .get("dataFilePath");

            log.info("開始處理行員檔案: {}", dataFilePath);
            batchLogService.saveLog(executionId, jobName, "INFO", "開始處理行員檔案: " + dataFilePath);

            // 檢查檔案是否存在
            File dataFile = new File(dataFilePath);
            if (!dataFile.exists()) {
                String errorMsg = "行員檔案不存在: " + dataFilePath;
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
                throw new RuntimeException(errorMsg);
            }

            // 讀取檔案行數
            long lineCount = Files.lines(Paths.get(dataFilePath)).count();
            log.info("檔案總行數: {}", lineCount);
            batchLogService.saveLog(executionId, jobName, "INFO", "檔案總行數: " + lineCount);

            // 模擬檔案處理邏輯
            // 在實際應用中，這裡會是具體的業務邏輯
            log.info("正在解析行員檔案...");
            batchLogService.saveLog(executionId, jobName, "INFO", "正在解析行員檔案...");

            // 模擬處理時間
            Thread.sleep(1000);

            // 處理完成，記錄統計資訊

            log.info("行員檔案處理完成，處理記錄數: {}", lineCount);
            batchLogService.saveLog(executionId, jobName, "INFO",
                    "行員檔案處理完成，處理記錄數: " + lineCount);

            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            log.error("行員檔案處理失敗", e);
            batchLogService.saveLog(executionId, jobName, "ERROR",
                    "行員檔案處理失敗: " + e.getMessage());
            throw e;
        }
    }
}