# 資料庫日誌記錄備用方案

## 問題背景

原有的 `DatabaseAppender` 使用 Spring 依賴注入來獲取 `BatchLogService`，但在某些環境下（例如不同的 classpath 配置）可能會出現 `batchLogService` 為 `null` 的問題，導致日誌無法正常記錄到資料庫。

## 備用方案

為了解決這個問題，我們提供了一個**不依賴 Spring 容器**的備用方案 `DirectDatabaseAppender`，它直接通過 JDBC 連接到資料庫。

## 方案特點

### DirectDatabaseAppender 的優勢：
1. **不依賴 Spring 容器**：直接使用 JDBC 連接，避免依賴注入問題
2. **非同步處理**：使用背景執行緒處理日誌，避免阻塞主程序
3. **自動重試機制**：連接失敗時可以自動重試
4. **記憶體安全**：使用有界佇列，避免記憶體溢出
5. **優雅關閉**：應用關閉時會等待剩餘日誌處理完成

### 原有 DatabaseAppender 的優勢：
1. **事務管理**：利用 Spring 的事務管理功能
2. **連接池**：使用 Spring 配置的連接池
3. **配置靈活**：可以通過 Spring 配置調整資料庫參數

## 使用方法

### 1. 檢查當前配置狀態

```bash
curl -X GET http://localhost:8089/api/log-test/check-config
```

### 2. 切換到備用方案

```bash
curl -X POST http://localhost:8089/api/log-test/switch-to-direct
```

### 3. 測試日誌記錄

```bash
curl -X POST http://localhost:8089/api/log-test/test-current
```

### 4. 切換回原始方案

```bash
curl -X POST http://localhost:8089/api/log-test/switch-to-spring
```

### 5. 批量測試（性能測試）

```bash
curl -X POST http://localhost:8089/api/log-test/batch-test/100
```

## 配置檔案

### 原始配置 (`log4j2.xml`)
```xml
<!-- 使用 Spring 依賴注入的方案 -->
<DatabaseAppender name="DatabaseAppender"/>
```

### 備用配置 (`log4j2-backup.xml`)
```xml
<!-- 使用直接 JDBC 連接的方案 -->
<DirectDatabaseAppender name="DirectDatabaseAppender"/>
```

## 資料庫配置

在 `DirectDatabaseAppender` 中，資料庫連接參數是硬編碼的：

```java
private static final String DB_URL = "jdbc:postgresql://192.168.31.247:5444/postgres?useUnicode=true&characterEncoding=utf8&useSSL=false&currentSchema=public";
private static final String DB_USERNAME = "root";
private static final String DB_PASSWORD = "VZq9rWbC3oJYFYdDrjT6edewVHQEKNCBWPDnyqxKyzMTE3CoozBrWnYsi6KkpwKujcFKDytQCrxhTbcxsAB2vswcVgQc9ieYvtpP";
```

如果需要在不同環境使用不同的資料庫連接，可以：

1. **方案一**：修改程式碼中的常數
2. **方案二**：使用系統屬性或環境變數
3. **方案三**：讀取外部配置檔案

## 使用建議

### 正常情況下
使用原有的 `DatabaseAppender`（Spring 依賴注入方案），因為它有更好的事務管理和連接池支援。

### 出現問題時
1. 先檢查 `BatchLogConfig` 是否正常執行
2. 確認 `batchLogService` 是否成功注入
3. 如果注入失敗，切換到 `DirectDatabaseAppender`

### 長期使用備用方案
如果需要長期使用 `DirectDatabaseAppender`，建議：
1. 增加連接池支援
2. 增加配置檔案支援
3. 增加更完善的錯誤處理

## 故障排除

### 1. 如果切換失敗
- 檢查 `log4j2-backup.xml` 檔案是否存在
- 檢查檔案內容是否正確
- 查看控制台錯誤訊息

### 2. 如果日誌不記錄到資料庫
- 確認 MDC 中有 `executionId`
- 檢查資料庫連接是否正常
- 查看控制台錯誤訊息

### 3. 如果性能問題
- 調整佇列大小（預設 10000）
- 檢查資料庫連接延遲
- 考慮批量插入優化

## 程式碼檔案

- `DirectDatabaseAppender.java`：備用 Appender 實現
- `LogAppenderSwitcher.java`：配置切換工具
- `LogTestController.java`：測試介面
- `log4j2-backup.xml`：備用配置檔案
- `BACKUP_LOG_SOLUTION.md`：說明文件

## 注意事項

1. **資料庫密碼安全**：目前密碼是硬編碼的，生產環境需要更安全的方式
2. **連接池**：目前每次記錄都建立新連接，可能影響性能
3. **事務**：沒有事務支援，如果需要可以考慮增加
4. **配置**：目前配置是硬編碼的，可以考慮支援外部配置

這個備用方案可以在 Spring 依賴注入出現問題時確保日誌記錄功能的正常運作。 