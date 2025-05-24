package cub.ret.tru.batch.service.query;

import cub.ret.tru.batch.dto.LogQueryCriteria;
import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.repository.BatchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultQueryStrategy implements LogQueryStrategy {
    
    private final BatchLogRepository repository;
    
    @Override
    public boolean supports(LogQueryCriteria criteria) {
        return true; // 預設策略，總是支援
    }
    
    @Override
    public List<BatchLogEntity> execute(LogQueryCriteria criteria) {
        log.debug("使用預設查詢策略");
        
        int limit = criteria.getLimit() != null ? criteria.getLimit() : 100;
        return repository.findRecentLogsNative(limit);
    }
    
    @Override
    public int getPriority() {
        return Integer.MAX_VALUE; // 最低優先級
    }
}