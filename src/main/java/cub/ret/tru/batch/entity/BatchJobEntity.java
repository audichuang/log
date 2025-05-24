package cub.ret.tru.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 批次作業實體
 */
@Entity
@Table(name = "batch_job_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobEntity {

    @Id
    @Column(name = "job_id", length = 50)
    private String id;

    /**
     * 作業名稱（Spring Batch Job 名稱）
     */
    @Column(name = "job_name", nullable = false, length = 100)
    private String name;

    /**
     * 顯示名稱
     */
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /**
     * 作業描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 作業狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    /**
     * 最後執行時間
     */
    @Column(name = "last_execution_time")
    private LocalDateTime lastExecutionTime;

    /**
     * 最後執行代號
     */
    @Column(name = "last_execution_id", length = 50)
    private String lastExecutionId;

    /**
     * 下次預定執行時間
     */
    @Column(name = "next_scheduled_time")
    private LocalDateTime nextScheduledTime;

    /**
     * 是否為排程作業
     */
    @Column(name = "is_scheduled", nullable = false)
    private Boolean isScheduled;

    /**
     * 是否啟用
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /**
     * 作業分類
     */
    @Column(name = "category", length = 50)
    private String category;

    /**
     * 預估執行時間（分鐘）
     */
    @Column(name = "estimated_duration")
    private Integer estimatedDuration;

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
        if (status == null) {
            status = JobStatus.IDLE;
        }
        if (enabled == null) {
            enabled = true;
        }
        if (isScheduled == null) {
            isScheduled = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 作業狀態枚舉
     */
    public enum JobStatus {
        IDLE("待命中"),
        RUNNING("執行中"),
        COMPLETED("已完成"),
        FAILED("執行失敗");

        private final String description;

        JobStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
} 