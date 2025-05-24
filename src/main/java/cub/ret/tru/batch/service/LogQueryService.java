// file: src/main/java/cub/ret/tru/batch/service/LogQueryService.java
package cub.ret.tru.batch.service;

import cub.ret.tru.batch.dto.LogQueryCriteria;
import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.repository.BatchLogRepository;
import cub.ret.tru.specification.BatchLogSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogQueryService {

    private final BatchLogRepository repository;

    public List<BatchLogEntity> queryLogs(LogQueryCriteria criteria) {
        log.debug("執行日誌查詢，條件: {}", criteria);

        // 參數驗證和預處理
        criteria = validateAndPreprocessCriteria(criteria);

        // 如果是簡單的執行ID查詢，直接使用方法查詢
        if (isSimpleExecutionIdQuery(criteria)) {
            log.debug("使用簡單執行ID查詢");
            return repository.findByExecutionIdOrderByLogTimeDesc(criteria.getExecutionId());
        }

        // 如果是空條件，返回最近日誌
        if (criteria.isEmpty()) {
            log.debug("使用預設最近日誌查詢");
            return repository.findRecentLogs(criteria.getLimit());
        }

        // 使用 Specification 進行動態查詢
        log.debug("使用 Specification 動態查詢");
        Specification<BatchLogEntity> spec = BatchLogSpecifications.buildSpecification(criteria);

        List<BatchLogEntity> results;
        if (criteria.getLimit() != null && criteria.getLimit() > 0) {
            PageRequest pageRequest = PageRequest.of(0, criteria.getLimit());
            results = repository.findAll(spec, pageRequest).getContent();
        } else {
            results = repository.findAll(spec);
        }

        log.debug("查詢完成，返回 {} 筆記錄", results.size());
        return results;
    }

    private boolean isSimpleExecutionIdQuery(LogQueryCriteria criteria) {
        return StringUtils.hasText(criteria.getExecutionId()) &&
                !StringUtils.hasText(criteria.getJobName()) &&
                !StringUtils.hasText(criteria.getStepName()) &&
                !StringUtils.hasText(criteria.getKeyword()) &&
                (criteria.getLogLevels() == null || criteria.getLogLevels().isEmpty()) &&
                criteria.getStartTime() == null &&
                criteria.getEndTime() == null;
    }

    private LogQueryCriteria validateAndPreprocessCriteria(LogQueryCriteria criteria) {
        if (criteria == null) {
            throw new IllegalArgumentException("查詢條件不能為空");
        }

        // 時間範圍驗證
        if (criteria.getStartTime() != null && criteria.getEndTime() != null) {
            if (criteria.getStartTime().isAfter(criteria.getEndTime())) {
                throw new IllegalArgumentException("開始時間不能晚於結束時間");
            }
        }

        // 限制數量驗證和預設值設置
        if (criteria.getLimit() == null || criteria.getLimit() <= 0) {
            criteria.setLimit(100);
        } else if (criteria.getLimit() > 1000) {
            criteria.setLimit(1000);
        }

        // 排序方向預設值
        if (criteria.getSortDirection() == null) {
            criteria.setSortDirection("DESC");
        }

        return criteria;
    }
}