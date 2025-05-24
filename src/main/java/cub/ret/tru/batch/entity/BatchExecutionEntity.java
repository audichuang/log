package cub.ret.tru.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 批次執行記錄實體
 */
@Entity
@Table(name = "batch_execution")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchExecutionEntity {

    @Id
    @Column(name = "execution_id", length = 50)
    private String executionId;

    /**
     * 作業名稱
     */
    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    /**
     * 執行狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExecutionStatus status;

    /**
     * 開始時間
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * 結束時間
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * 執行訊息
     */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /**
     * 錯誤訊息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 處理的記錄數
     */
    @Column(name = "processed_count")
    private Long processedCount;

    /**
     * 成功的記錄數
     */
    @Column(name = "success_count")
    private Long successCount;

    /**
     * 失敗的記錄數
     */
    @Column(name = "failed_count")
    private Long failedCount;

    /**
     * 建立時間
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 更新時間
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (startTime == null) {
            startTime = now;
        }
        if (status == null) {
            status = ExecutionStatus.RUNNING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 執行狀態枚舉
     */
    public enum ExecutionStatus {
        RUNNING("執行中"),
        COMPLETED("已完成"),
        FAILED("執行失敗"),
        STOPPED("已停止");

        private final String description;

        ExecutionStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
} 