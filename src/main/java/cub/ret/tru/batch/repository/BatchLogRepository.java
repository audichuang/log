package cub.ret.tru.batch.repository;

import cub.ret.tru.batch.entity.BatchLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批次日誌 Repository
 */
@Repository
public interface BatchLogRepository extends JpaRepository<BatchLogEntity, Long> {

    /**
     * 根據執行代號查詢日誌
     */
    List<BatchLogEntity> findByExecutionIdOrderByLogTimeAsc(String executionId);

    /**
     * 根據作業名稱查詢日誌
     */
    List<BatchLogEntity> findByJobNameOrderByLogTimeDesc(String jobName);

    /**
     * 根據執行代號和作業名稱查詢日誌
     */
    List<BatchLogEntity> findByExecutionIdAndJobNameOrderByLogTimeAsc(String executionId, String jobName);

    /**
     * 根據日誌級別查詢日誌
     */
    List<BatchLogEntity> findByLogLevelOrderByLogTimeDesc(String logLevel);

    /**
     * 根據時間範圍查詢日誌
     */
    @Query("SELECT bl FROM BatchLogEntity bl WHERE bl.logTime BETWEEN :startTime AND :endTime ORDER BY bl.logTime DESC")
    List<BatchLogEntity> findByLogTimeBetween(@Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查詢指定執行代號的錯誤日誌
     */
    @Query("SELECT bl FROM BatchLogEntity bl WHERE bl.executionId = :executionId AND bl.logLevel IN ('ERROR', 'WARN') ORDER BY bl.logTime ASC")
    List<BatchLogEntity> findErrorLogsByExecutionId(@Param("executionId") String executionId);

    /**
     * 刪除指定天數之前的日誌
     */
    @Modifying
    @Query("DELETE FROM BatchLogEntity bl WHERE bl.logTime < :cutoffDate")
    void deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * 統計指定執行代號的日誌數量按級別分組
     */
    @Query("SELECT bl.logLevel, COUNT(bl) FROM BatchLogEntity bl WHERE bl.executionId = :executionId GROUP BY bl.logLevel")
    List<Object[]> countLogsByLevelForExecution(@Param("executionId") String executionId);
}