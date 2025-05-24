package cub.ret.tru.batch.service.query;

import cub.ret.tru.batch.dto.LogQueryCriteria;
import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.repository.BatchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TimeRangeQueryStrategy implements LogQueryStrategy {
    
    private final BatchLogRepository repository;
    
    @Override
    public boolean supports(LogQueryCriteria criteria) {
        return criteria.getExecutionId() == null && 
               criteria.getJobName() == null &&
               (criteria.getLogLevels() == null || criteria.getLogLevels().isEmpty()) &&
               !StringUtils.hasText(criteria.getKeyword()) &&
               criteria.getStartTime() != null && 
               criteria.getEndTime() != null;
    }
    
    @Override
    public List<BatchLogEntity> execute(LogQueryCriteria criteria) {
        log.debug("使用時間範圍查詢策略: {} - {}", criteria.getStartTime(), criteria.getEndTime());
        
        return repository.findByTimeRangeOnly(
                criteria.getStartTime(),
                criteria.getEndTime());
    }
    
    @Override
    public int getPriority() {
        return 5;
    }
}