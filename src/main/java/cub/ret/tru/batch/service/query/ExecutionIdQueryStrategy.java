package cub.ret.tru.batch.service.query;

import cub.ret.tru.batch.dto.LogQueryCriteria;
import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.repository.BatchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

// 執行ID查詢策略
@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionIdQueryStrategy implements LogQueryStrategy {

    private final BatchLogRepository repository;

    @Override
    public boolean supports(LogQueryCriteria criteria) {
        return StringUtils.hasText(criteria.getExecutionId());
    }

    @Override
    public List<BatchLogEntity> execute(LogQueryCriteria criteria) {
        log.debug("使用執行ID查詢策略: {}", criteria.getExecutionId());

        if (criteria.getStartTime() != null || criteria.getEndTime() != null) {
            return repository.findByExecutionIdAndTimeRange(
                    criteria.getExecutionId(),
                    criteria.getStartTime(),
                    criteria.getEndTime());
        }

        if ("ASC".equals(criteria.getSortDirection())) {
            return repository.findByExecutionIdOrderByLogTimeAsc(criteria.getExecutionId());
        }
        return repository.findByExecutionIdOrderByLogTimeDesc(criteria.getExecutionId());
    }

    @Override
    public int getPriority() {
        return 1; // 最高優先級
    }

}