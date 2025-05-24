package cub.ret.tru.batch.service.query;

import cub.ret.tru.batch.dto.LogQueryCriteria;
import cub.ret.tru.batch.entity.BatchLogEntity;
import java.util.List;

public interface LogQueryStrategy {
    boolean supports(LogQueryCriteria criteria);
    List<BatchLogEntity> execute(LogQueryCriteria criteria);
    int getPriority();
} 