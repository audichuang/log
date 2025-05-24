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
public class JobNameQueryStrategy implements LogQueryStrategy {
    
    private final BatchLogRepository repository;
    
    @Override
    public boolean supports(LogQueryCriteria criteria) {
        return criteria.getExecutionId() == null && 
               StringUtils.hasText(criteria.getJobName());
    }
    
    @Override
    public List<BatchLogEntity> execute(LogQueryCriteria criteria) {
        log.debug("使用作業名稱查詢策略: {}", criteria.getJobName());
        
        if (criteria.getStartTime() != null || criteria.getEndTime() != null) {
            return repository.findByJobNameAndTimeRange(
                    criteria.getJobName(),
                    criteria.getStartTime(),
                    criteria.getEndTime());
        }
        
        return repository.findByJobNameOrderByLogTimeDesc(criteria.getJobName());
    }
    
    @Override
    public int getPriority() {
        return 2;
    }
}