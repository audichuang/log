package cub.ret.tru.batch.service;

import cub.ret.tru.batch.dto.LogQueryCriteria;
import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.service.query.LogQueryStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class LogQueryService {

    private final List<LogQueryStrategy> strategies;

    @Autowired
    public LogQueryService(List<LogQueryStrategy> strategies) {
        this.strategies = strategies.stream()
                .sorted(Comparator.comparing(LogQueryStrategy::getPriority))
                .toList();

        log.info("已載入 {} 個查詢策略", strategies.size());
        strategies.forEach(strategy ->
                log.debug("策略: {} - 優先級: {}",
                        strategy.getClass().getSimpleName(),
                        strategy.getPriority()));
    }

    public List<BatchLogEntity> queryLogs(LogQueryCriteria criteria) {
        log.debug("執行日誌查詢，條件: {}", criteria);

        // 參數驗證和預處理
        criteria = validateAndPreprocessCriteria(criteria);

        // 選擇合適的查詢策略
        LogQueryStrategy strategy = selectStrategy(criteria);
        log.debug("選擇策略: {}", strategy.getClass().getSimpleName());

        try {
            // 執行查詢
            List<BatchLogEntity> results = strategy.execute(criteria);

            // 後處理結果
            results = postProcessResults(results, criteria);

            log.debug("查詢完成，返回 {} 筆記錄", results.size());
            return results;

        } catch (Exception e) {
            log.error("查詢執行失敗", e);
            throw new RuntimeException("查詢執行失敗: " + e.getMessage(), e);
        }
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

    private LogQueryStrategy selectStrategy(LogQueryCriteria criteria) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(criteria))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("找不到合適的查詢策略"));
    }

    private List<BatchLogEntity> postProcessResults(List<BatchLogEntity> results, LogQueryCriteria criteria) {
        // 如果結果超過限制，進行截取
        if (results.size() > criteria.getLimit()) {
            return results.subList(0, criteria.getLimit());
        }
        return results;
    }
}
