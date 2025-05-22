package cub.ret.tru.batch.util;

import org.slf4j.MDC;

/**
 * MDC 工具類
 * 提供與 ExecutionContextHolder 相同的 API，但基於 MDC 實現
 * 支援異步日誌和更好的上下文管理
 */
public class MDCUtil {

    // MDC 鍵常量
    public static final String EXECUTION_ID = "executionId";
    public static final String JOB_NAME = "jobName";
    public static final String STEP_NAME = "stepName";

    /**
     * 設定當前執行代號
     */
    public static void setCurrentExecutionId(String executionId) {
        MDC.put(EXECUTION_ID, executionId);
    }

    /**
     * 獲取當前執行代號
     */
    public static String getCurrentExecutionId() {
        return MDC.get(EXECUTION_ID);
    }

    /**
     * 設定當前作業名稱
     */
    public static void setCurrentJobName(String jobName) {
        MDC.put(JOB_NAME, jobName);
    }

    /**
     * 獲取當前作業名稱
     */
    public static String getCurrentJobName() {
        return MDC.get(JOB_NAME);
    }

    /**
     * 設定當前步驟名稱
     */
    public static void setCurrentStepName(String stepName) {
        MDC.put(STEP_NAME, stepName);
    }

    /**
     * 獲取當前步驟名稱
     */
    public static String getCurrentStepName() {
        return MDC.get(STEP_NAME);
    }

    /**
     * 清除當前執行緒的所有上下文資訊
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * 清除執行代號
     */
    public static void clearExecutionId() {
        MDC.remove(EXECUTION_ID);
    }

    /**
     * 清除作業名稱
     */
    public static void clearJobName() {
        MDC.remove(JOB_NAME);
    }

    /**
     * 清除步驟名稱
     */
    public static void clearStepName() {
        MDC.remove(STEP_NAME);
    }

    /**
     * 設定完整的上下文信息
     */
    public static void setContext(String executionId, String jobName, String stepName) {
        setCurrentExecutionId(executionId);
        setCurrentJobName(jobName);
        if (stepName != null) {
            setCurrentStepName(stepName);
        }
    }

    /**
     * 設定作業上下文（不含步驟）
     */
    public static void setJobContext(String executionId, String jobName) {
        setCurrentExecutionId(executionId);
        setCurrentJobName(jobName);
    }
}