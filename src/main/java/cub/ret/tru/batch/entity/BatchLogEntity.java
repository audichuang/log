package cub.ret.tru.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 批次作業日誌實體
 */
@Entity
@Table(name = "batch_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 執行代號 - 每次批次執行的唯一識別碼
     */
    @Column(name = "execution_id", nullable = false, length = 50)
    private String executionId;

    /**
     * 作業名稱
     */
    @Column(name = "job_name", length = 100)
    private String jobName;

    /**
     * 步驟名稱
     */
    @Column(name = "step_name", length = 100)
    private String stepName;

    /**
     * 日誌級別
     */
    @Column(name = "log_level", length = 10)
    private String logLevel;

    /**
     * 日誌訊息
     */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /**
     * 異常堆疊信息
     */
    @Column(name = "exception_stack", columnDefinition = "TEXT")
    private String exceptionStack;

    /**
     * 日誌記錄時間
     */
    @Column(name = "log_time", nullable = false)
    private LocalDateTime logTime;

    /**
     * 日誌來源類別
     */
    @Column(name = "logger_name", length = 200)
    private String loggerName;

    /**
     * 執行緒名稱
     */
    @Column(name = "thread_name", length = 100)
    private String threadName;

    /**
     * 額外資訊
     */
    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo;

    /**
     * 建立時間
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (logTime == null) {
            logTime = LocalDateTime.now();
        }
    }
} 