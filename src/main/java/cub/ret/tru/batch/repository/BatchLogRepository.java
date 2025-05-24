package cub.ret.tru.batch.repository;

import cub.ret.tru.batch.entity.BatchLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BatchLogRepository extends JpaRepository<BatchLogEntity, Long> {

    // 基本查詢方法
    List<BatchLogEntity> findByExecutionIdOrderByLogTimeAsc(String executionId);
    List<BatchLogEntity> findByExecutionIdOrderByLogTimeDesc(String executionId);
    List<BatchLogEntity> findByJobNameOrderByLogTimeDesc(String jobName);
    List<BatchLogEntity> findByLogLevelOrderByLogTimeDesc(String logLevel);
    
    // 獲取最近日誌 - 使用原生查詢避免 LIMIT 問題
    @Query(value = "SELECT * FROM batch_log ORDER BY log_time DESC LIMIT :limit", nativeQuery = true)
    List<BatchLogEntity> findRecentLogsNative(@Param("limit") int limit);
    
    // 根據執行ID和時間範圍查詢
    @Query("SELECT bl FROM BatchLogEntity bl WHERE bl.executionId = :executionId " +
           "AND (:startTime IS NULL OR bl.logTime >= :startTime) " +
           "AND (:endTime IS NULL OR bl.logTime <= :endTime) " +
           "ORDER BY bl.logTime DESC")
    List<BatchLogEntity> findByExecutionIdAndTimeRange(
            @Param("executionId") String executionId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    // 根據作業名稱和時間範圍查詢
    @Query("SELECT bl FROM BatchLogEntity bl WHERE bl.jobName = :jobName " +
           "AND (:startTime IS NULL OR bl.logTime >= :startTime) " +
           "AND (:endTime IS NULL OR bl.logTime <= :endTime) " +
           "ORDER BY bl.logTime DESC")
    List<BatchLogEntity> findByJobNameAndTimeRange(
            @Param("jobName") String jobName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    // 根據日誌級別查詢（支援多個級別）
    @Query("SELECT bl FROM BatchLogEntity bl WHERE bl.logLevel IN :logLevels " +
           "AND (:startTime IS NULL OR bl.logTime >= :startTime) " +
           "AND (:endTime IS NULL OR bl.logTime <= :endTime) " +
           "ORDER BY bl.logTime DESC")
    List<BatchLogEntity> findByLogLevelsAndTimeRange(
            @Param("logLevels") List<String> logLevels,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    // 關鍵字搜尋查詢
    @Query("SELECT bl FROM BatchLogEntity bl WHERE " +
           "(LOWER(bl.message) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(bl.loggerName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:startTime IS NULL OR bl.logTime >= :startTime) " +
           "AND (:endTime IS NULL OR bl.logTime <= :endTime) " +
           "ORDER BY bl.logTime DESC")
    List<BatchLogEntity> findByKeywordAndTimeRange(
            @Param("keyword") String keyword,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    // 時間範圍查詢
    @Query("SELECT bl FROM BatchLogEntity bl WHERE " +
           "bl.logTime >= :startTime AND bl.logTime <= :endTime " +
           "ORDER BY bl.logTime DESC")
    List<BatchLogEntity> findByTimeRangeOnly(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    // 實時查詢（用於SSE）
    @Query(value = "SELECT * FROM batch_log WHERE log_time > :afterTime " +
           "ORDER BY log_time DESC LIMIT :limit", nativeQuery = true)
    List<BatchLogEntity> findRecentLogsAfter(
            @Param("afterTime") LocalDateTime afterTime, 
            @Param("limit") int limit);
    
    // 統計查詢
    @Query("SELECT bl.logLevel, COUNT(bl) FROM BatchLogEntity bl " +
           "WHERE bl.executionId = :executionId GROUP BY bl.logLevel")
    List<Object[]> countLogsByLevelForExecution(@Param("executionId") String executionId);
}