# 批次作業管理系統 (Batch Job Management System)

這是一個基於 Angular 14 和 Material Design 的批次作業管理前端應用程式，提供批次作業執行和日誌查看功能。

## 功能特色

### 🚀 批次作業管理
- **作業列表展示**：以表格形式展示所有批次作業
- **即時狀態監控**：顯示作業執行狀態（執行中、已完成、失敗、待命）
- **一鍵執行**：點擊執行按鈕即可啟動批次作業
- **作業分類**：按類別組織不同的批次作業
- **執行歷史**：顯示最後執行時間和執行代號

### 📊 日誌查看系統
- **彈窗式日誌查看器**：Material Design 風格的日誌查看彈窗
- **多層級過濾**：
  - 日誌級別過濾（DEBUG、INFO、WARN、ERROR）
  - 關鍵字搜尋
  - 時間範圍篩選
- **表格展示**：可排序的日誌表格
- **分頁功能**：支持大量日誌的分頁瀏覽
- **例外堆疊查看**：點擊圖示查看詳細錯誤信息

### 📈 統計資訊
- **即時統計卡片**：顯示各狀態作業數量
- **視覺化狀態指示**：不同顏色表示不同執行狀態
- **響應式設計**：適配桌面、平板和手機

## 技術架構

- **前端框架**：Angular 14
- **UI 組件庫**：Angular Material 14
- **樣式語言**：SCSS
- **狀態管理**：RxJS
- **HTTP 客戶端**：Angular HttpClient
- **響應式設計**：CSS Grid + Flexbox

## 安裝和運行

### 前置需求
- Node.js 16+ 
- npm 8+

### 安裝依賴
```bash
npm install
```

### 開發模式運行
```bash
npm start
```
應用程式將在 `http://localhost:4200` 啟動

### 生產環境構建
```bash
npm run build
```

## 使用說明

### 1. 批次作業管理
1. 進入主頁面，查看所有可用的批次作業
2. 每個作業顯示：
   - 作業名稱和描述
   - 分類標籤
   - 當前執行狀態
   - 最後執行時間
   - 預估執行時間
3. 點擊「執行」按鈕啟動作業
4. 點擊「查看日誌」按鈕開啟日誌查看器

### 2. 日誌查看
1. 在日誌彈窗中可以：
   - 選擇日誌級別進行過濾
   - 輸入關鍵字搜尋特定內容
   - 點擊表格標題進行排序
   - 使用分頁器瀏覽大量日誌
2. 如果日誌包含例外信息，會顯示錯誤圖示
3. 點擊「重新載入」按鈕刷新日誌

### 3. 響應式設計
- **桌面版**：完整功能展示，多欄位表格
- **平板版**：調整佈局，保持核心功能
- **手機版**：簡化介面，垂直排列操作按鈕

## 項目結構

```
src/
├── app/
│   ├── components/
│   │   ├── batch-control/          # 批次作業控制組件
│   │   ├── log-dialog/             # 日誌查看彈窗組件
│   │   ├── log-filter/             # 日誌過濾組件
│   │   └── log-viewer/             # 日誌查看器組件
│   ├── models/
│   │   └── batch-log.model.ts      # 數據模型定義
│   ├── services/
│   │   └── batch.service.ts        # 批次作業服務
│   └── environments/               # 環境配置
├── assets/                         # 靜態資源
└── styles.scss                     # 全局樣式
```

## API 集成

應用程式設計為與 Spring Boot 後端 API 集成：

### 批次作業 API
- `GET /api/batch/jobs` - 獲取作業列表
- `POST /api/batch/jobs/{jobId}/run` - 執行特定作業
- `GET /api/batch/executions/{executionId}` - 獲取執行狀態

### 日誌 API
- `GET /api/batch/logs/execution/{executionId}` - 按執行代號查詢日誌
- `GET /api/batch/logs/job/{jobName}` - 按作業名稱查詢日誌
- `GET /api/batch/logs/time-range` - 按時間範圍查詢日誌

## 開發注意事項

### 樣式規範
- 使用 SCSS 進行樣式開發
- 遵循 Material Design 設計原則
- 支持深色/淺色主題切換
- 響應式設計優先

### 組件設計
- 組件間通過服務進行通信
- 使用 RxJS 處理異步操作
- 錯誤處理和載入狀態管理
- 可重用組件設計

### 性能優化
- 懶加載路由
- OnPush 變更檢測策略
- 虛擬滾動（大數據量）
- 圖片和資源優化

## 瀏覽器支持

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## 授權

此項目僅供學習和開發使用 