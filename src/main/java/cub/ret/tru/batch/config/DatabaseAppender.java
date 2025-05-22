package cub.ret.tru.batch.config;

import cub.ret.tru.batch.service.BatchLogService;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

/**
 * 自定義Log4j2資料庫Appender
 */
@Plugin(name = "DatabaseAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class DatabaseAppender extends AbstractAppender {

    private static BatchLogService batchLogService;

    protected DatabaseAppender(String name, Filter filter) {
        super(name, filter, null, true, null);
    }

    @Override
    public void append(LogEvent event) {
        try {
            if (batchLogService != null) {
                // 優先從 MDC 獲取上下文信息（支援異步日誌）
                String executionId = event.getContextData().getValue("executionId");
                String jobName = event.getContextData().getValue("jobName");
                String stepName = event.getContextData().getValue("stepName");

                // 如果 MDC 中沒有執行代號，跳過記錄到資料庫
                // 現在完全依賴 MDC，不再使用 ExecutionContextHolder

                // 如果沒有執行代號，跳過記錄到資料庫
                if (executionId == null) {
                    return;
                }

                String logLevel = event.getLevel().name();
                String message = event.getMessage().getFormattedMessage();
                String loggerName = event.getLoggerName();
                String threadName = event.getThreadName();
                String exceptionStack = null;

                // 處理異常資訊
                if (event.getThrown() != null) {
                    StringBuilder sb = new StringBuilder();
                    Throwable throwable = event.getThrown();
                    sb.append(throwable.toString()).append("\n");
                    for (StackTraceElement element : throwable.getStackTrace()) {
                        sb.append("\tat ").append(element.toString()).append("\n");
                    }
                    exceptionStack = sb.toString();
                }

                batchLogService.saveLog(executionId, jobName, stepName, logLevel,
                        message, loggerName, threadName, exceptionStack);
            }
        } catch (Exception e) {
            // 避免日誌記錄失敗影響主程序
            System.err.println("Failed to append log to database: " + e.getMessage());
        }
    }

    @PluginFactory
    public static DatabaseAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter) {

        if (name == null) {
            LOGGER.error("No name provided for DatabaseAppender");
            return null;
        }

        return new DatabaseAppender(name, filter);
    }

    /**
     * 設定 BatchLogService 實例
     * 這個方法會被 Spring 容器調用
     */
    public static void setBatchLogService(BatchLogService service) {
        batchLogService = service;
    }
}