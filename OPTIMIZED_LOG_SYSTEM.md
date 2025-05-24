# 優化日誌查詢系統

## 架構概述

本系統使用策略模式重構了日誌查詢邏輯，提供了更清晰的架構和更好的性能。

### 核心組件

1. **LogQueryCriteria** - 統一的查詢條件管理
2. **LogQueryStrategy** - 查詢策略介面
3. **LogQueryService** - 核心查詢服務
4. **OptimizedLogStreamService** - SSE 串流服務
5. **OptimizedLogController** - 統一的 API 控制器

## 主要改進

### 1. 策略模式架構
- `ExecutionIdQueryStrategy` - 執行ID查詢（最高優先級）
- `CompositeQueryStrategy` - 複合條件查詢
- `DefaultQueryStrategy` - 預設查詢策略

### 2. 統一查詢條件
```java
LogQueryCriteria criteria = LogQueryCriteria.builder()
    .executionId("12345")
    .jobName("GET_EMPLOYEE_JOB")
    .stepName("dataValidationStep")
    .logLevels(Arrays.asList("ERROR", "WARN"))
    .keyword("validation")
    .startTime(LocalDateTime.now().minusHours(1))
    .endTime(LocalDateTime.now())
    .limit(100)
    .sortDirection("DESC")
    .queryType(LogQueryType.HISTORICAL)
    .build();
```

### 3. 優化的 Repository
新增了動態條件查詢方法：
```java
@Query("SELECT bl FROM BatchLogEntity bl WHERE " +
       "(:#{#criteria.executionId} IS NULL OR bl.executionId = :#{#criteria.executionId}) AND " +
       "(:#{#criteria.jobName} IS NULL OR bl.jobName = :#{#criteria.jobName}) AND " +
       // ... 更多條件
       "ORDER BY bl.logTime DESC")
List<BatchLogEntity> findByDynamicConditions(@Param("criteria") LogQueryCriteria criteria);
```

## API 端點

### 1. 統一查詢端點
```
GET /api/batch/logs
```
參數：
- `executionId` - 執行ID
- `jobName` - 作業名稱
- `stepName` - 步驟名稱
- `logLevels` - 日誌級別列表
- `keyword` - 關鍵字
- `startTime` - 開始時間 (ISO格式)
- `endTime` - 結束時間 (ISO格式)
- `limit` - 限制數量 (1-1000)
- `sortDirection` - 排序方向 (ASC/DESC)
- `queryType` - 查詢類型 (REAL_TIME/HISTORICAL/SEARCH)

### 2. POST 複雜查詢
```
POST /api/batch/logs/query
Content-Type: application/json

{
  "executionId": "12345",
  "jobName": "GET_EMPLOYEE_JOB",
  "logLevels": ["ERROR", "WARN"],
  "startTime": "2024-01-01T00:00:00",
  "endTime": "2024-01-01T23:59:59",
  "limit": 100
}
```

### 3. SSE 實時串流
```
GET /api/batch/logs/stream
```
事件類型：
- `connected` - 連接建立
- `logs` - 日誌數據

### 4. 串流管理
```
PUT /api/batch/logs/stream/{connectionId}/filter   # 更新過濾條件
DELETE /api/batch/logs/stream/{connectionId}        # 關閉連接
GET /api/batch/logs/stream/status                   # 獲取狀態
```

### 5. 統計資訊
```
GET /api/batch/logs/stats/{executionId}
```
返回：
```json
{
  "totalCount": 1500,
  "levelCounts": {
    "INFO": 1200,
    "WARN": 250,
    "ERROR": 50
  },
  "executionId": "12345"
}
```

## 前端集成範例

### Angular 服務
```typescript
@Injectable()
export class OptimizedLogService {
  constructor(private http: HttpClient) {}
  
  // 統一查詢
  queryLogs(criteria: LogQueryCriteria): Observable<BatchLogEntity[]> {
    return this.http.post<BatchLogEntity[]>('/api/batch/logs/query', criteria);
  }
  
  // SSE 串流
  streamLogs(criteria: LogQueryCriteria): Observable<BatchLogEntity[]> {
    return new Observable(observer => {
      const params = this.buildParams(criteria);
      const eventSource = new EventSource(`/api/batch/logs/stream?${params}`);
      
      eventSource.addEventListener('logs', event => {
        observer.next(JSON.parse(event.data));
      });
      
      eventSource.addEventListener('connected', event => {
        console.log('SSE 連接已建立');
      });
      
      eventSource.onerror = () => observer.error('SSE 連接錯誤');
      
      return () => eventSource.close();
    });
  }
  
  // 獲取統計
  getLogStats(executionId: string): Observable<LogStats> {
    return this.http.get<LogStats>(`/api/batch/logs/stats/${executionId}`);
  }
}
```

### 組件使用
```typescript
export class LogViewerComponent {
  logs$ = new BehaviorSubject<BatchLogEntity[]>([]);
  
  queryLogs() {
    const criteria: LogQueryCriteria = {
      executionId: this.selectedExecutionId,
      jobName: this.selectedJobName,
      logLevels: this.selectedLogLevels,
      startTime: this.startTime,
      endTime: this.endTime,
      limit: 100,
      queryType: 'HISTORICAL'
    };
    
    this.logService.queryLogs(criteria).subscribe(logs => {
      this.logs$.next(logs);
    });
  }
  
  startRealTimeStream() {
    const criteria: LogQueryCriteria = {
      queryType: 'REAL_TIME',
      limit: 50
    };
    
    this.logService.streamLogs(criteria).subscribe(newLogs => {
      const currentLogs = this.logs$.value;
      this.logs$.next([...newLogs, ...currentLogs]);
    });
  }
}
```

## 性能優化建議

### 1. 資料庫索引
```sql
-- 建議添加的索引
CREATE INDEX idx_batch_log_execution_time ON batch_log(execution_id, log_time);
CREATE INDEX idx_batch_log_job_time ON batch_log(job_name, log_time);
CREATE INDEX idx_batch_log_level_time ON batch_log(log_level, log_time);
CREATE INDEX idx_batch_log_time ON batch_log(log_time);
CREATE INDEX idx_batch_log_keyword ON batch_log USING gin(to_tsvector('english', message));
```

### 2. 應用配置
```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 100
          fetch_size: 50
        query:
          plan_cache_max_size: 2048
  task:
    execution:
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 100

logging:
  level:
    cub.ret.tru.batch.service: DEBUG
    org.hibernate.SQL: INFO
```

### 3. JVM 調優
```bash
-Xms2g -Xmx4g
-XX:+UseG1GC
-XX:G1HeapRegionSize=16m
-XX:+UnlockExperimentalVMOptions
-XX:+UseStringDeduplication
```

## 特色功能

1. **自動策略選擇** - 根據查詢條件自動選擇最佳查詢策略
2. **實時串流** - 支援 SSE 實時日誌推送
3. **連接管理** - 自動管理 SSE 連接生命週期
4. **錯誤恢復** - 完善的錯誤處理和重連機制
5. **統計分析** - 提供日誌級別統計和趨勢分析
6. **靈活過濾** - 支援多維度組合查詢條件

## 監控和維護

### 1. 健康檢查
```
GET /api/batch/logs/stream/status
```

### 2. 日誌清理
建議定期清理舊日誌：
```sql
DELETE FROM batch_log WHERE log_time < NOW() - INTERVAL '30 days';
```

### 3. 連接監控
監控活躍的 SSE 連接數量，避免記憶體洩漏。 