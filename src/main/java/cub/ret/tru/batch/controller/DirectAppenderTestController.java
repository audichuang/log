package cub.ret.tru.batch.controller;

import cub.ret.tru.batch.config.DirectDatabaseAppender;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.StringMap;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * DirectDatabaseAppender 測試控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/direct-appender-test")
public class DirectAppenderTestController {

    /**
     * 直接測試 DirectDatabaseAppender
     */
    @PostMapping("/test")
    public String testDirectAppender() {
        try {
            // 創建 DirectDatabaseAppender 實例
            DirectDatabaseAppender appender = DirectDatabaseAppender.createAppender("TestAppender", null);

            if (appender == null) {
                return "無法創建 DirectDatabaseAppender 實例";
            }

            // 創建測試用的 LogEvent
            StringMap contextData = new SortedArrayStringMap();
            contextData.putValue("executionId", "DIRECT_APPENDER_TEST_" + System.currentTimeMillis());
            contextData.putValue("jobName", "DIRECT_APPENDER_TEST_JOB");
            contextData.putValue("stepName", "DIRECT_APPENDER_TEST_STEP");

            LogEvent logEvent = Log4jLogEvent.newBuilder()
                    .setLoggerName("DirectAppenderTestController")
                    .setLevel(Level.INFO)
                    .setMessage(new SimpleMessage("DirectDatabaseAppender 直接測試"))
                    .setTimeMillis(Instant.now().toEpochMilli())
                    .setThreadName(Thread.currentThread().getName())
                    .setContextData(contextData)
                    .build();

            // 調用 append 方法
            appender.append(logEvent);

            // 等待一下讓非同步處理完成
            Thread.sleep(2000);

            // 停止 appender
            appender.stop();

            return "DirectDatabaseAppender 測試完成！執行代號: " + contextData.getValue("executionId");

        } catch (Exception e) {
            log.error("DirectDatabaseAppender 測試失敗", e);
            return "DirectDatabaseAppender 測試失敗: " + e.getMessage();
        }
    }
}