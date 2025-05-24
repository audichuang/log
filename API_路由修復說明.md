# API 路由衝突修復說明

## 問題描述

在啟動後端服務時遇到路由映射衝突錯誤：

```
Ambiguous mapping. Cannot map 'optimizedLogController' method 
cub.ret.tru.batch.controller.OptimizedLogController#closeStream(String)
to {DELETE [/api/batch/logs/stream/{connectionId}]}: There is already 'batchJobController' bean method
cub.ret.tru.batch.controller.BatchJobController#closeStream(String) mapped.
```

## 原因分析

兩個控制器定義了相同的路由映射：

### 1. BatchJobController (現有的)
- **基本路徑**: `@RequestMapping("/api/batch")`
- **SSE 路由**: `@DeleteMapping("/logs/stream/{connectionId}")`
- **最終路由**: `DELETE /api/batch/logs/stream/{connectionId}`

### 2. OptimizedLogController (新增的)
- **基本路徑**: `@RequestMapping("/api/batch/logs")` ❌
- **SSE 路由**: `@DeleteMapping("/stream/{connectionId}")`
- **最終路由**: `DELETE /api/batch/logs/stream/{connectionId}` ❌

兩個控制器產生了完全相同的路由，導致 Spring 無法決定使用哪個處理器。

## 解決方案

### 修改 OptimizedLogController 的基本路徑

將 `OptimizedLogController` 的基本路徑從 `/api/batch/logs` 改為 `/api/batch/optimized-logs`：

```java
@RestController
@RequestMapping("/api/batch/optimized-logs")  // 修改後
public class OptimizedLogController {
    // ...
}
```

## 修復後的路由結構

### BatchJobController (保持不變)
```
GET    /api/batch/jobs
GET    /api/batch/jobs/{jobId}
POST   /api/batch/jobs/{jobId}/run
POST   /api/batch/executions/{executionId}/stop
GET    /api/batch/executions/{executionId}
GET    /api/batch/jobs/{jobName}/executions
GET    /api/batch/logs/stream                    (SSE)
POST   /api/batch/logs/stream/{connectionId}/filter
DELETE /api/batch/logs/stream/{connectionId}
GET    /api/batch/logs/stream/status
```

### OptimizedLogController (修復後)
```
GET    /api/batch/optimized-logs                 (統一查詢)
POST   /api/batch/optimized-logs/query           (複雜查詢)
GET    /api/batch/optimized-logs/stream          (SSE)
PUT    /api/batch/optimized-logs/stream/{connectionId}/filter
DELETE /api/batch/optimized-logs/stream/{connectionId}
GET    /api/batch/optimized-logs/stream/status
GET    /api/batch/optimized-logs/stats/{executionId}
```

## 前端對應修改

### 更新 OptimizedLogService 中的 API 路徑

```typescript
// 修改前
queryLogs(criteria: LogQueryCriteria): Observable<BatchLog[]> {
    return this.http.get<BatchLog[]>(`${this.apiUrl}/batch/logs`, { params });
}

// 修改後
queryLogs(criteria: LogQueryCriteria): Observable<BatchLog[]> {
    return this.http.get<BatchLog[]>(`${this.apiUrl}/batch/optimized-logs`, { params });
}
```

所有相關的 API 端點都已相應更新：
- `/batch/logs` → `/batch/optimized-logs`
- `/batch/logs/query` → `/batch/optimized-logs/query`
- `/batch/logs/stream` → `/batch/optimized-logs/stream`
- `/batch/logs/stats/{executionId}` → `/batch/optimized-logs/stats/{executionId}`

## 架構優勢

這種修復方式保持了清晰的 API 架構：

### 1. 功能分離
- **BatchJobController**: 負責傳統的作業管理和基本日誌串流
- **OptimizedLogController**: 負責優化的日誌查詢和高級功能

### 2. 向後兼容
- 現有的前端代碼仍然可以使用 `/api/batch/logs/stream`
- 新的優化功能使用 `/api/batch/optimized-logs/*`

### 3. 漸進式升級
- 系統可以在兩種模式之間切換
- 便於逐步遷移和功能測試

## 測試結果

### 後端編譯
```
✅ Maven 編譯成功
✅ 無路由衝突錯誤
✅ 所有測試通過 (4/4)
```

### 前端編譯
```
✅ Angular 編譯成功
✅ TypeScript 類型檢查通過
✅ 所有 API 路徑已更新
```

## 後續建議

1. **API 文檔更新**: 更新 Swagger 或 API 文檔以反映新的路由結構
2. **監控設置**: 為新的 API 端點添加監控和日誌記錄
3. **性能測試**: 測試新舊 API 的性能差異
4. **逐步遷移**: 考慮將舊的 SSE 功能逐步遷移到新的優化版本

## 總結

通過簡單的路徑調整，成功解決了路由衝突問題，同時保持了：
- ✅ 功能完整性
- ✅ 向後兼容性
- ✅ 清晰的架構分離
- ✅ 測試覆蓋率

系統現在可以正常啟動，並提供兩套並行的日誌管理 API。 