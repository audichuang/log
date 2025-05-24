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
}