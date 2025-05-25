package cub.ret.tru.batch.repository;

import cub.ret.tru.batch.entity.BatchLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BatchLogRepository extends JpaRepository<BatchLogEntity, Long>, JpaSpecificationExecutor<BatchLogEntity> {

    // 基本查詢方法
    List<BatchLogEntity> findByExecutionIdOrderByLogTimeDesc(String executionId);

    // 獲取最近日誌
    @Query(value = "SELECT * FROM batch_log ORDER BY log_time DESC LIMIT :limit", nativeQuery = true)
    List<BatchLogEntity> findRecentLogs(@Param("limit") int limit);

    // 實時查詢（用於SSE）
    @Query(value = "SELECT * FROM batch_log WHERE log_time > :afterTime ORDER BY log_time DESC LIMIT :limit", nativeQuery = true)
    List<BatchLogEntity> findRecentLogsAfter(@Param("afterTime") LocalDateTime afterTime, @Param("limit") int limit);

    // 統計查詢
    @Query("SELECT bl.logLevel, COUNT(bl) FROM BatchLogEntity bl WHERE bl.executionId = :executionId GROUP BY bl.logLevel")
    List<Object[]> countLogsByLevelForExecution(@Param("executionId") String executionId);

    // 查詢特定作業的所有執行代號
    @Query("SELECT DISTINCT bl.executionId FROM BatchLogEntity bl WHERE bl.jobName = :jobName ORDER BY bl.executionId DESC")
    List<String> findDistinctExecutionIdsByJobName(@Param("jobName") String jobName);

    // ID增量查詢 - 根據最後ID查詢更新的日誌
    @Query(value = "SELECT * FROM batch_log WHERE id > :lastId AND job_name = :jobName ORDER BY id ASC LIMIT :limit", nativeQuery = true)
    List<BatchLogEntity> findIncrementalLogsByIdAndJobName(@Param("lastId") Long lastId, @Param("jobName") String jobName, @Param("limit") int limit);

    // ID增量查詢 - 根據最後ID查詢更新的日誌（支援執行ID過濾）
    @Query(value = "SELECT * FROM batch_log WHERE id > :lastId AND execution_id = :executionId ORDER BY id ASC LIMIT :limit", nativeQuery = true)
    List<BatchLogEntity> findIncrementalLogsByIdAndExecutionId(@Param("lastId") Long lastId, @Param("executionId") String executionId, @Param("limit") int limit);

    // ID增量查詢 - 根據最後ID查詢更新的日誌（通用版本，可支援多種條件）
    @Query(value = "SELECT * FROM batch_log WHERE id > :lastId ORDER BY id ASC LIMIT :limit", nativeQuery = true)
    List<BatchLogEntity> findIncrementalLogsById(@Param("lastId") Long lastId, @Param("limit") int limit);
}