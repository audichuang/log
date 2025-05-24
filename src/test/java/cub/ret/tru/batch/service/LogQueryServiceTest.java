package cub.ret.tru.batch.service;

import cub.ret.tru.batch.dto.LogQueryCriteria;
import cub.ret.tru.batch.entity.BatchLogEntity;
import cub.ret.tru.batch.service.query.LogQueryStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LogQueryServiceTest {

    @Mock
    private LogQueryStrategy mockStrategy1;
    
    @Mock
    private LogQueryStrategy mockStrategy2;

    private LogQueryService logQueryService;

    @BeforeEach
    void setUp() {
        // 設定模擬策略優先級
        when(mockStrategy1.getPriority()).thenReturn(1);
        when(mockStrategy2.getPriority()).thenReturn(2);
        
        List<LogQueryStrategy> strategies = Arrays.asList(mockStrategy1, mockStrategy2);
        logQueryService = new LogQueryService(strategies);
    }

    @Test
    void testQueryLogsWithValidCriteria() {
        // 準備測試數據
        LogQueryCriteria criteria = LogQueryCriteria.builder()
                .executionId("test-123")
                .limit(100)
                .build();
        
        BatchLogEntity mockLog = BatchLogEntity.builder()
                .id(1L)
                .executionId("test-123")
                .logLevel("INFO")
                .message("Test message")
                .logTime(LocalDateTime.now())
                .build();
        
        List<BatchLogEntity> expectedLogs = Arrays.asList(mockLog);
        
        // 設定模擬行為
        when(mockStrategy1.supports(criteria)).thenReturn(true);
        when(mockStrategy1.execute(criteria)).thenReturn(expectedLogs);
        
        // 執行測試
        List<BatchLogEntity> result = logQueryService.queryLogs(criteria);
        
        // 驗證結果
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test-123", result.get(0).getExecutionId());
        
        // 驗證策略調用
        verify(mockStrategy1).supports(criteria);
        verify(mockStrategy1).execute(criteria);
        verify(mockStrategy2, never()).execute(any());
    }

    @Test
    void testQueryLogsWithInvalidTimeRange() {
        // 準備無效的時間範圍
        LogQueryCriteria criteria = LogQueryCriteria.builder()
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().minusHours(1)) // 結束時間早於開始時間
                .build();
        
        // 驗證拋出異常
        assertThrows(IllegalArgumentException.class, () -> {
            logQueryService.queryLogs(criteria);
        });
    }

    @Test
    void testQueryLogsWithNullCriteria() {
        // 驗證 null 參數拋出異常
        assertThrows(IllegalArgumentException.class, () -> {
            logQueryService.queryLogs(null);
        });
    }

    @Test
    void testStrategySelection() {
        LogQueryCriteria criteria = LogQueryCriteria.builder()
                .jobName("TEST_JOB")
                .build();
        
        // 設定第一個策略不支援，第二個支援
        when(mockStrategy1.supports(criteria)).thenReturn(false);
        when(mockStrategy2.supports(criteria)).thenReturn(true);
        when(mockStrategy2.execute(criteria)).thenReturn(Arrays.asList());
        
        // 執行測試
        logQueryService.queryLogs(criteria);
        
        // 驗證策略選擇
        verify(mockStrategy1).supports(criteria);
        verify(mockStrategy2).supports(criteria);
        verify(mockStrategy1, never()).execute(any());
        verify(mockStrategy2).execute(criteria);
    }
} 