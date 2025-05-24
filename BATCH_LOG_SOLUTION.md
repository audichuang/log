# 批次作業管理和日誌系統

## 系統概述

這是一個完整的批次作業管理和日誌查看系統，支援：
- 批次作業的執行、停止和狀態監控
- 實時日誌串流（SSE）
- 日誌過濾和搜索
- 響應式前端界面

## 技術架構

### 後端 (Spring Boot)
- **Spring Batch**: 批次作業框架
- **Spring Data JPA**: 數據持久化
- **PostgreSQL**: 數據庫
- **SSE (Server-Sent Events)**: 實時日誌串流
- **RESTful API**: 前後端通信

### 前端 (Angular 14)
- **Angular Material**: UI 組件庫
- **RxJS**: 響應式編程
- **EventSource**: SSE 客戶端
- **TypeScript**: 類型安全

## 核心功能

### 1. 批次作業管理
- 查看所有批次作業列表
- 執行批次作業
- 停止正在執行的作業
- 查看作業執行歷史

### 2. SSE 日誌串流
- **無過濾條件**: 自動輪詢最新日誌（每2秒）
- **有過濾條件**: 立即查詢並返回結果，停止輪詢
- **動態過濾**: 可以隨時更新過濾條件

### 3. 日誌過濾功能
- 執行代號 (executionId)
- 作業名稱 (jobName)
- 日誌級別 (logLevel)
- 關鍵字搜索 (keyword)
- 時間範圍 (startTime, endTime)

## API 端點

### 批次作業管理
```
GET    /api/batch/jobs                    # 獲取所有作業
GET    /api/batch/jobs/{jobId}            # 獲取特定作業
POST   /api/batch/jobs/{jobId}/run        # 執行作業
POST   /api/batch/executions/{id}/stop    # 停止作業
GET    /api/batch/executions/{id}         # 獲取執行記錄
```

### SSE 日誌串流
```
GET    /api/batch/logs/stream             # 建立 SSE 連接
POST   /api/batch/logs/stream/{id}/filter # 更新過濾條件
DELETE /api/batch/logs/stream/{id}        # 關閉連接
GET    /api/batch/logs/stream/status      # 獲取連接狀態
```

## SSE 工作原理

### 1. 建立連接
```typescript
// 前端建立 SSE 連接
const eventSource = new EventSource('/api/batch/logs/stream?executionId=exec-001');
```

### 2. 輪詢機制
- **無過濾條件**: 後端每2秒查詢新日誌並推送
- **有過濾條件**: 立即查詢並返回，不進行輪詢

### 3. 過濾條件更新
```typescript
// 更新過濾條件會重新建立連接
this.batchService.updateLogFilter({
    executionId: 'exec-001',
    logLevel: 'ERROR'
});
```

### 4. 連接管理
- 自動重連機制（5秒後重試）
- 連接狀態監控
- 優雅關閉處理

## 數據庫設計

### 批次作業表 (batch_job_info)
```sql
CREATE TABLE batch_job_info (
    job_id VARCHAR(50) PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    last_execution_time TIMESTAMP,
    last_execution_id VARCHAR(50),
    -- ... 其他欄位
);
```

### 執行記錄表 (batch_execution)
```sql
CREATE TABLE batch_execution (
    execution_id VARCHAR(50) PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    -- ... 其他欄位
);
```

### 日誌表 (batch_log)
```sql
CREATE TABLE batch_log (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(50) NOT NULL,
    job_name VARCHAR(100),
    log_level VARCHAR(10),
    message TEXT,
    log_time TIMESTAMP NOT NULL,
    -- ... 其他欄位
);
```

## 啟動指南

### 1. 後端啟動
```bash
cd /Users/audi/Downloads/log
mvn spring-boot:run
```

### 2. 前端啟動
```bash
cd frontend
npm install
npm start
```

### 3. 訪問應用
- 前端: http://localhost:4200
- 後端 API: http://localhost:8080

## 使用場景

### 場景1: 監控所有日誌
1. 打開應用，不設置任何過濾條件
2. 系統自動建立 SSE 連接
3. 每2秒自動獲取最新日誌

### 場景2: 查看特定作業日誌
1. 點擊作業的「查看日誌」按鈕
2. 彈出日誌對話框，設置 executionId 過濾
3. 立即查詢並顯示該作業的所有日誌
4. 停止輪詢，只顯示過濾結果

### 場景3: 錯誤日誌排查
1. 設置日誌級別為 "ERROR"
2. 設置關鍵字搜索
3. 立即獲取符合條件的錯誤日誌

## 技術特點

### 1. 智能輪詢策略
- 無過濾條件時自動輪詢
- 有過濾條件時停止輪詢，避免資源浪費

### 2. 連接管理
- 自動重連機制
- 連接狀態監控
- 內存中管理多個 SSE 連接

### 3. 性能優化
- 數據庫索引優化
- 分頁查詢支援
- 連接池管理

### 4. 錯誤處理
- 優雅的錯誤處理
- 用戶友好的錯誤提示
- 自動重試機制

## 擴展功能

### 1. 日誌導出
- 支援 CSV、Excel 格式導出
- 大量數據分批導出

### 2. 實時通知
- WebSocket 支援
- 郵件通知
- 釘釘/Slack 集成

### 3. 監控面板
- 作業執行統計
- 性能指標監控
- 告警規則配置

### 4. 權限管理
- 用戶角色管理
- 作業執行權限
- 日誌查看權限

## 故障排除

### 1. SSE 連接失敗
- 檢查後端服務是否正常
- 確認 CORS 配置
- 查看瀏覽器控制台錯誤

### 2. 日誌不更新
- 檢查過濾條件是否正確
- 確認數據庫中是否有新日誌
- 查看後端日誌

### 3. 作業執行失敗
- 檢查作業配置
- 查看執行日誌
- 確認數據庫連接

## 總結

這個系統提供了完整的批次作業管理和日誌監控解決方案，通過 SSE 技術實現了實時日誌串流，並且具有智能的輪詢策略。系統架構清晰，易於擴展和維護。 