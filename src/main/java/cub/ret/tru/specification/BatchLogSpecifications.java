package cub.ret.tru.specification;

import cub.ret.tru.batch.dto.LogQueryCriteria;
import cub.ret.tru.batch.entity.BatchLogEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class BatchLogSpecifications {

    public static Specification<BatchLogEntity> buildSpecification(LogQueryCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 執行ID
            if (StringUtils.hasText(criteria.getExecutionId())) {
                predicates.add(criteriaBuilder.equal(root.get("executionId"), criteria.getExecutionId()));
            }

            // 作業名稱
            if (StringUtils.hasText(criteria.getJobName())) {
                predicates.add(criteriaBuilder.equal(root.get("jobName"), criteria.getJobName()));
            }

            // 步驟名稱
            if (StringUtils.hasText(criteria.getStepName())) {
                predicates.add(criteriaBuilder.equal(root.get("stepName"), criteria.getStepName()));
            }

            // 日誌級別（支援多個）
            if (criteria.getLogLevels() != null && !criteria.getLogLevels().isEmpty()) {
                predicates.add(root.get("logLevel").in(criteria.getLogLevels()));
            }

            // 關鍵字搜尋
            if (StringUtils.hasText(criteria.getKeyword())) {
                String keyword = "%" + criteria.getKeyword().toLowerCase() + "%";
                Predicate messagePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("message")), keyword);
                Predicate loggerPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("loggerName")), keyword);
                predicates.add(criteriaBuilder.or(messagePredicate, loggerPredicate));
            }

            // 時間範圍
            if (criteria.getStartTime() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("logTime"), criteria.getStartTime()));
            }
            if (criteria.getEndTime() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("logTime"), criteria.getEndTime()));
            }

            // 排序
            if ("ASC".equals(criteria.getSortDirection())) {
                query.orderBy(criteriaBuilder.asc(root.get("logTime")));
            } else {
                query.orderBy(criteriaBuilder.desc(root.get("logTime")));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}