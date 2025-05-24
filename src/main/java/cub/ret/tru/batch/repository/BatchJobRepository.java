package cub.ret.tru.batch.repository;

import cub.ret.tru.batch.entity.BatchJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 批次作業 Repository
 */
@Repository
public interface BatchJobRepository extends JpaRepository<BatchJobEntity, String> {

    /**
     * 根據作業名稱查詢
     */
    Optional<BatchJobEntity> findByName(String name);

    /**
     * 查詢所有啟用的作業
     */
    List<BatchJobEntity> findByEnabledTrueOrderByUpdatedAtDesc();

    /**
     * 根據分類查詢作業
     */
    List<BatchJobEntity> findByCategoryOrderByUpdatedAtDesc(String category);

    /**
     * 根據狀態查詢作業
     */
    List<BatchJobEntity> findByStatusOrderByUpdatedAtDesc(BatchJobEntity.JobStatus status);

    /**
     * 查詢排程作業
     */
    @Query("SELECT j FROM BatchJobEntity j WHERE j.isScheduled = true AND j.enabled = true ORDER BY j.nextScheduledTime ASC")
    List<BatchJobEntity> findScheduledJobs();

    /**
     * 統計各狀態的作業數量
     */
    @Query("SELECT j.status, COUNT(j) FROM BatchJobEntity j GROUP BY j.status")
    List<Object[]> countByStatus();
} 