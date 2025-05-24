package cub.ret.tru.batch.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * 日誌 Appender 切換工具
 * 允許在運行時切換不同的日誌配置
 */
@Component
public class LogAppenderSwitcher {

    /**
     * 切換到備用的直接資料庫連接方案
     */
    public void switchToDirectDatabaseAppender() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);

            // 載入備用配置
            InputStream configStream = getClass().getClassLoader()
                    .getResourceAsStream("log4j2-backup.xml");

            if (configStream != null) {
                ConfigurationSource source = new ConfigurationSource(configStream);
                Configuration config = new XmlConfiguration(context, source);

                // 應用新配置
                context.setConfiguration(config);
                context.reconfigure(config);

                System.out.println("已切換至 DirectDatabaseAppender 配置");
            } else {
                System.err.println("找不到 log4j2-backup.xml 配置檔案");
            }
        } catch (Exception e) {
            System.err.println("切換日誌配置失敗: " + e.getMessage());
        }
    }

    /**
     * 切換回原始的 Spring 依賴注入方案
     */
    public void switchToSpringDatabaseAppender() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);

            // 載入原始配置
            InputStream configStream = getClass().getClassLoader()
                    .getResourceAsStream("log4j2.xml");

            if (configStream != null) {
                ConfigurationSource source = new ConfigurationSource(configStream);
                Configuration config = new XmlConfiguration(context, source);

                // 應用新配置
                context.setConfiguration(config);
                context.reconfigure(config);

                System.out.println("已切換至 Spring DatabaseAppender 配置");
            } else {
                System.err.println("找不到 log4j2.xml 配置檔案");
            }
        } catch (Exception e) {
            System.err.println("切換日誌配置失敗: " + e.getMessage());
        }
    }

    /**
     * 檢查當前配置狀態
     */
    public void checkCurrentConfiguration() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration config = context.getConfiguration();

            System.out.println("當前日誌配置:");
            System.out.println("- 配置名稱: " + config.getName());
            System.out.println("- Appenders: " + config.getAppenders().keySet());

            // 檢查是否有 DatabaseAppender 或 DirectDatabaseAppender
            boolean hasSpringAppender = config.getAppenders().containsKey("DatabaseAppender");
            boolean hasDirectAppender = config.getAppenders().containsKey("DirectDatabaseAppender");

            if (hasSpringAppender) {
                System.out.println("- 當前使用: Spring DatabaseAppender");
            }
            if (hasDirectAppender) {
                System.out.println("- 當前使用: DirectDatabaseAppender");
            }
            if (!hasSpringAppender && !hasDirectAppender) {
                System.out.println("- 當前沒有啟用資料庫 Appender");
            }

        } catch (Exception e) {
            System.err.println("檢查配置失敗: " + e.getMessage());
        }
    }
}