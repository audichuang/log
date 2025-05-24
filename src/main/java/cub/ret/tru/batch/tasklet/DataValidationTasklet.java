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

import java.util.Random;

/**
 * 資料驗證 Tasklet - 產生各種類型的日誌記錄
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataValidationTasklet implements Tasklet {

    private final BatchLogService batchLogService;
    private final Random random = new Random();

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        String executionId = MDCUtil.getCurrentExecutionId();
        String jobName = MDCUtil.getCurrentJobName();

        log.debug("【DEBUG】開始資料驗證程序");
        batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】開始資料驗證程序");

        try {
            // 階段1: 資料格式驗證
            performFormatValidation(executionId, jobName);
            
            // 階段2: 業務規則驗證
            performBusinessRuleValidation(executionId, jobName);
            
            // 階段3: 資料完整性檢查
            performIntegrityCheck(executionId, jobName);
            
            // 階段4: 效能監控
            performPerformanceMonitoring(executionId, jobName);

            log.info("資料驗證程序完成");
            batchLogService.saveLog(executionId, jobName, "INFO", "資料驗證程序完成");

            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            log.error("資料驗證程序失敗", e);
            batchLogService.saveLog(executionId, jobName, "ERROR", "資料驗證程序失敗: " + e.getMessage());
            throw e;
        }
    }

    private void performFormatValidation(String executionId, String jobName) throws InterruptedException {
        log.info("=== 階段1: 資料格式驗證 ===");
        batchLogService.saveLog(executionId, jobName, "INFO", "=== 階段1: 資料格式驗證 ===");

        log.debug("【DEBUG】載入格式驗證規則");
        batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】載入格式驗證規則");

        // 模擬格式驗證過程
        for (int i = 1; i <= 50; i++) {
            log.debug("【DEBUG】驗證記錄 {} - 檢查欄位格式", i);
            batchLogService.saveLog(executionId, jobName, "DEBUG", 
                String.format("【DEBUG】驗證記錄 %d - 檢查欄位格式", i));

            // 隨機產生格式錯誤
            if (random.nextFloat() < 0.08f) { // 8% 機率
                String warnMsg = String.format("【格式警告】記錄 %d 的日期格式不符合標準", i);
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            // 隨機產生嚴重格式錯誤
            if (random.nextFloat() < 0.03f) { // 3% 機率
                String errorMsg = String.format("【格式錯誤】記錄 %d 含有非法字元", i);
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
            }

            if (i % 10 == 0) {
                Thread.sleep(50); // 模擬處理時間
                log.info("格式驗證進度: {}/50", i);
                batchLogService.saveLog(executionId, jobName, "INFO", 
                    String.format("格式驗證進度: %d/50", i));
            }
        }

        log.info("格式驗證完成");
        batchLogService.saveLog(executionId, jobName, "INFO", "格式驗證完成");
    }

    private void performBusinessRuleValidation(String executionId, String jobName) throws InterruptedException {
        log.info("=== 階段2: 業務規則驗證 ===");
        batchLogService.saveLog(executionId, jobName, "INFO", "=== 階段2: 業務規則驗證 ===");

        log.debug("【DEBUG】載入業務規則引擎");
        batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】載入業務規則引擎");

        // 隨機產生規則引擎警告
        if (random.nextFloat() < 0.2f) { // 20% 機率
            String warnMsg = "【規則引擎警告】某些業務規則可能已過期，建議更新";
            log.warn(warnMsg);
            batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
        }

        String[] businessRules = {
            "薪資範圍驗證", "部門編碼檢查", "職位層級驗證", 
            "入職日期合理性", "員工編號唯一性", "聯絡資訊完整性"
        };

        for (String rule : businessRules) {
            log.debug("【DEBUG】執行業務規則: {}", rule);
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】執行業務規則: " + rule);

            Thread.sleep(200); // 模擬規則執行時間

            // 隨機產生業務規則警告
            if (random.nextFloat() < 0.15f) { // 15% 機率
                String warnMsg = String.format("【業務警告】規則 '%s' 發現異常數據", rule);
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            // 隨機產生業務規則錯誤
            if (random.nextFloat() < 0.05f) { // 5% 機率
                String errorMsg = String.format("【業務錯誤】規則 '%s' 驗證失敗", rule);
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
            }

            log.info("業務規則 '{}' 執行完成", rule);
            batchLogService.saveLog(executionId, jobName, "INFO", "業務規則 '" + rule + "' 執行完成");
        }

        log.info("業務規則驗證完成");
        batchLogService.saveLog(executionId, jobName, "INFO", "業務規則驗證完成");
    }

    private void performIntegrityCheck(String executionId, String jobName) throws InterruptedException {
        log.info("=== 階段3: 資料完整性檢查 ===");
        batchLogService.saveLog(executionId, jobName, "INFO", "=== 階段3: 資料完整性檢查 ===");

        log.debug("【DEBUG】建立完整性檢查索引");
        batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】建立完整性檢查索引");

        // 隨機產生索引建立警告
        if (random.nextFloat() < 0.1f) { // 10% 機率
            String warnMsg = "【索引警告】索引建立耗時較長，可能影響效能";
            log.warn(warnMsg);
            batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
        }

        String[] integrityChecks = {
            "外鍵關聯檢查", "唯一性約束驗證", "非空值檢查", "參照完整性驗證"
        };

        for (String check : integrityChecks) {
            log.debug("【DEBUG】執行完整性檢查: {}", check);
            batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】執行完整性檢查: " + check);

            Thread.sleep(300); // 模擬檢查時間

            // 隨機產生完整性警告
            if (random.nextFloat() < 0.12f) { // 12% 機率
                String warnMsg = String.format("【完整性警告】'%s' 發現潛在問題", check);
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            // 隨機產生完整性錯誤
            if (random.nextFloat() < 0.04f) { // 4% 機率
                String errorMsg = String.format("【完整性錯誤】'%s' 檢查失敗", check);
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
            }

            log.info("完整性檢查 '{}' 完成", check);
            batchLogService.saveLog(executionId, jobName, "INFO", "完整性檢查 '" + check + "' 完成");
        }

        log.info("資料完整性檢查完成");
        batchLogService.saveLog(executionId, jobName, "INFO", "資料完整性檢查完成");
    }

    private void performPerformanceMonitoring(String executionId, String jobName) throws InterruptedException {
        log.info("=== 階段4: 效能監控 ===");
        batchLogService.saveLog(executionId, jobName, "INFO", "=== 階段4: 效能監控 ===");

        log.debug("【DEBUG】啟動效能監控器");
        batchLogService.saveLog(executionId, jobName, "DEBUG", "【DEBUG】啟動效能監控器");

        // 模擬效能監控
        for (int i = 1; i <= 10; i++) {
            Thread.sleep(100);

            // 模擬CPU使用率
            int cpuUsage = 30 + random.nextInt(50);
            log.debug("【DEBUG】CPU使用率: {}%", cpuUsage);
            batchLogService.saveLog(executionId, jobName, "DEBUG", 
                String.format("【DEBUG】CPU使用率: %d%%", cpuUsage));

            // 模擬內存使用率
            int memoryUsage = 40 + random.nextInt(45);
            log.debug("【DEBUG】內存使用率: {}%", memoryUsage);
            batchLogService.saveLog(executionId, jobName, "DEBUG", 
                String.format("【DEBUG】內存使用率: %d%%", memoryUsage));

            // 高CPU使用率警告
            if (cpuUsage > 70) {
                String warnMsg = String.format("【效能警告】CPU使用率過高: %d%%", cpuUsage);
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            // 高內存使用率警告
            if (memoryUsage > 75) {
                String warnMsg = String.format("【效能警告】內存使用率過高: %d%%", memoryUsage);
                log.warn(warnMsg);
                batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
            }

            // 隨機產生效能錯誤
            if (random.nextFloat() < 0.03f) { // 3% 機率
                String errorMsg = "【效能錯誤】系統資源不足，處理速度下降";
                log.error(errorMsg);
                batchLogService.saveLog(executionId, jobName, "ERROR", errorMsg);
            }
        }

        log.info("效能監控完成");
        batchLogService.saveLog(executionId, jobName, "INFO", "效能監控完成");

        // 最終效能報告
        double avgResponseTime = 150 + random.nextDouble() * 100;
        log.info("平均回應時間: {:.2f}ms", avgResponseTime);
        batchLogService.saveLog(executionId, jobName, "INFO", 
            String.format("平均回應時間: %.2f ms", avgResponseTime));

        if (avgResponseTime > 200) {
            String warnMsg = String.format("【效能警告】平均回應時間較慢: %.2f ms", avgResponseTime);
            log.warn(warnMsg);
            batchLogService.saveLog(executionId, jobName, "WARN", warnMsg);
        }
    }
} 