# Spring Batch 日誌系統

這是一個整合 Spring Batch、log4j2 和 JPA 的日誌系統，每次批次執行都會生成唯一的執行代號，並將日誌存儲到資料庫中。

## 功能特色

- ✅ **執行代號管理**：每次批次執行自動生成唯一執行代號（時間戳 + UUID）
- ✅ **日誌資料庫存儲**：使用 JPA 將日誌存儲到資料庫
- ✅ **log4j2 整合**：自定義 DatabaseAppender 自動將日誌寫入資料庫
- ✅ **執行上下文**：使用 ThreadLocal 管理執行代號和作業資訊
- ✅ **RESTful API**：提供日誌查詢和批次作業執行的 API
- ✅ **多種查詢方式**：支援按執行代號、作業名稱、時間範圍等查詢日誌

## 技術架構

- **Spring Boot 3.2.0**
- **Spring Batch 5.x**
- **Log4j2**
- **Spring Data JPA**
- **PostgreSQL Database**
- **Lombok**

## 專案結構

```
src/main/java/cub/ret/tru/batch/
├── config/
│   └── DatabaseAppender.java          # 自定義 log4j2 資料庫輸出器
├── controller/
│   └── BatchController.java           # 批次作業控制器
├── entity/
│   └── BatchLogEntity.java           # 日誌實體類
├── job/
│   └── GetEmployeeJobConfig.java     # 批次作業配置
├── listener/
│   └── base/
│       └── BaseJobListener.java      # 基礎作業監聽器
├── repository/
│   └── BatchLogRepository.java       # 日誌 Repository
├── service/
│   └── BatchLogService.java          # 日誌服務
├── tasklet/
│   ├── GetEmployeeJobTasklet.java    # 檔案處理 Tasklet
│   └── TrustBackupFileTasklet.java   # 檔案備份 Tasklet
└── util/
    └── ExecutionContextHolder.java   # 執行上下文持有者

src/main/resources/
├── application.yml                    # 應用程式配置
└── log4j2.xml                        # log4j2 配置
```

## 日誌實體欄位

| 欄位名稱 | 類型 | 說明 |
|---------|------|------|
| id | Long | 主鍵 |
| execution_id | String | 執行代號（每次執行的唯一識別碼）|
| job_name | String | 作業名稱 |
| step_name | String | 步驟名稱 |
| log_level | String | 日誌級別 |
| message | String | 日誌訊息 |
| exception_stack | String | 異常堆疊信息 |
| log_time | LocalDateTime | 日誌記錄時間 |
| logger_name | String | 日誌來源類別 |
| thread_name | String | 執行緒名稱 |
| additional_info | String | 額外資訊 |
| created_at | LocalDateTime | 建立時間 |

## 使用方法

### 1. 啟動應用程式

```bash
mvn spring-boot:run
```

### 2. 執行批次作業

```bash
# 手動執行批次作業
curl -X POST http://localhost:8080/api/batch/jobs/get-employee/run
```

### 3. 查詢日誌

```bash
# 根據執行代號查詢日誌
curl "http://localhost:8080/api/batch/logs/execution/{executionId}"

# 根據作業名稱查詢日誌
curl "http://localhost:8080/api/batch/logs/job/GET_EMPLOYEE_JOB"

# 查詢錯誤日誌
curl "http://localhost:8080/api/batch/logs/errors/{executionId}"

# 根據時間範圍查詢日誌
curl "http://localhost:8080/api/batch/logs/time-range?startTime=2023-01-01T00:00:00&endTime=2023-12-31T23:59:59"

# 查詢日誌統計
curl "http://localhost:8080/api/batch/logs/stats/{executionId}"
```

### 4. 資料庫連接資訊

PostgreSQL 資料庫連接資訊：
- 主機: `192.168.31.247:5444`
- 資料庫: `postgres`
- 用戶名: `root`
- 密碼: `VZq9rWbC3oJYFYdDrjT6edewVHQEKNCBWPDnyqxKyzMTE3CoozBrWnYsi6KkpwKujcFKDytQCrxhTbcxsAB2vswcVgQc9ieYvtpP`

## 執行代號生成邏輯

執行代號格式：`{yyyyMMddHHmmss}_{UUID前8位}`

例如：`20231201143022_a1b2c3d4`

- `20231201143022`：2023年12月1日 14:30:22
- `a1b2c3d4`：UUID 的前8位

## 配置說明

### 檔案路徑配置

在 `application.yml` 中可以設定：

```yaml
filePath: /tmp/batch/input        # 輸入檔案路徑
backupPath: /tmp/batch/backup     # 備份檔案路徑
```

### 資料庫配置

使用 PostgreSQL 資料庫：

```yaml
spring:
  datasource:
    # PostgreSQL 資料庫配置
    url: jdbc:postgresql://192.168.31.247:5444/postgres?useUnicode=true&characterEncoding=utf8&useSSL=false
    username: root
    password: VZq9rWbC3oJYFYdDrjT6edewVHQEKNCBWPDnyqxKyzMTE3CoozBrWnYsi6KkpwKujcFKDytQCrxhTbcxsAB2vswcVgQc9ieYvtpP
    driver-class-name: org.postgresql.Driver
```

## 日誌配置

log4j2.xml 配置了三種輸出方式：
1. **Console**：控制台輸出
2. **FileAppender**：檔案輸出
3. **DatabaseAppender**：資料庫輸出（自定義）

## 開發注意事項

1. **執行代號管理**：透過 `ExecutionContextHolder` ThreadLocal 管理
2. **事務管理**：日誌保存使用 `REQUIRES_NEW` 事務，避免主業務失敗影響日誌記錄
3. **錯誤處理**：所有組件都有完善的錯誤處理和日誌記錄
4. **可擴展性**：可以輕易擴展新的批次作業和日誌查詢功能

## 授權

MIT License 