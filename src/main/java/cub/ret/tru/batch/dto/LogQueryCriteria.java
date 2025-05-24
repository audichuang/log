package cub.ret.tru.batch.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogQueryCriteria {
    private String executionId;
    private String jobName;
    private String stepName;
    private List<String> logLevels;
    private String keyword;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer limit;
    private String sortDirection; // ASC, DESC
    private LogQueryType queryType;

    // 判斷是否為空查詢條件
    public boolean isEmpty() {
        return executionId == null && jobName == null && stepName == null &&
                (logLevels == null || logLevels.isEmpty()) && keyword == null &&
                startTime == null && endTime == null;
    }

    // 獲取查詢優先級
    public int getPriority() {
        if (executionId != null) return 1;
        if (jobName != null) return 2;
        if (logLevels != null && !logLevels.isEmpty()) return 3;
        if (keyword != null) return 4;
        if (startTime != null && endTime != null) return 5;
        return 6;
    }

    // 查詢類型枚舉
    public enum LogQueryType {
        REAL_TIME,    // 實時查詢
        HISTORICAL,   // 歷史查詢
        SEARCH        // 搜尋查詢
    }
}
