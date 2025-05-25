package cub.ret.tru.batch.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 查詢作業執行代號的請求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionIdsRequest {
    /**
     * 作業名稱
     */
    private String jobName;
} 