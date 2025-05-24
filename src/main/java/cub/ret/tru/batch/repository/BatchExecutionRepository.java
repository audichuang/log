package cub.ret.tru.batch.repository;

import cub.ret.tru.batch.entity.BatchExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批次執行記錄 Repository
 */
@Repository
public interface BatchExecutionRepository extends JpaRepository<BatchExecutionEntity, String> {

    /**
     * 根據作業名稱查詢執行記錄
     */
    List<BatchExecutionEntity> findByJobNameOrderByStartTimeDesc(String jobName);

    /**
     * 根據狀態查詢執行記錄
     */
    List<BatchExecutionEntity> findByStatusOrderByStartTimeDesc(BatchExecutionEntity.ExecutionStatus status);

    /**
     * 查詢最近的執行記錄
     */
    @Query("SELECT e FROM BatchExecutionEntity e ORDER BY e.startTime DESC")
    List<BatchExecutionEntity> findRecentExecutions();

    /**
     * 根據時間範圍查詢執行記錄
     */
    @Query("SELECT e FROM BatchExecutionEntity e WHERE e.startTime >= :startTime AND e.startTime <= :endTime ORDER BY e.startTime DESC")
    List<BatchExecutionEntity> findByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 查詢正在執行的作業
     */
    List<BatchExecutionEntity> findByStatusIn(List<BatchExecutionEntity.ExecutionStatus> statuses);

    /**
     * 根據作業名稱查詢最後一次執行記錄
     */
    @Query("SELECT e FROM BatchExecutionEntity e WHERE e.jobName = :jobName ORDER BY e.startTime DESC LIMIT 1")
    BatchExecutionEntity findLatestByJobName(@Param("jobName") String jobName);
} 