-- 自定義批次管理相關表結構

-- 批次作業資訊表
DROP TABLE IF EXISTS batch_job_info CASCADE;
CREATE TABLE batch_job_info (
    job_id VARCHAR(50) PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    last_execution_time TIMESTAMP,
    last_execution_id VARCHAR(50),
    next_scheduled_time TIMESTAMP,
    is_scheduled BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    category VARCHAR(50),
    estimated_duration INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 批次執行記錄表
DROP TABLE IF EXISTS batch_execution CASCADE;
CREATE TABLE batch_execution (
    execution_id VARCHAR(50) PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    message TEXT,
    error_message TEXT,
    processed_count BIGINT DEFAULT 0,
    success_count BIGINT DEFAULT 0,
    failed_count BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 批次日誌表
DROP TABLE IF EXISTS batch_log CASCADE;
CREATE TABLE batch_log (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(50) NOT NULL,
    job_name VARCHAR(100),
    step_name VARCHAR(100),
    log_level VARCHAR(10),
    message TEXT,
    exception_stack TEXT,
    log_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logger_name VARCHAR(200),
    thread_name VARCHAR(100),
    additional_info TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 創建索引以提升查詢效能
CREATE INDEX idx_batch_job_info_status ON batch_job_info(status);
CREATE INDEX idx_batch_job_info_enabled ON batch_job_info(enabled);
CREATE INDEX idx_batch_job_info_category ON batch_job_info(category);

CREATE INDEX idx_batch_execution_job_name ON batch_execution(job_name);
CREATE INDEX idx_batch_execution_status ON batch_execution(status);
CREATE INDEX idx_batch_execution_start_time ON batch_execution(start_time);

CREATE INDEX idx_batch_log_execution_id ON batch_log(execution_id);
CREATE INDEX idx_batch_log_job_name ON batch_log(job_name);
CREATE INDEX idx_batch_log_log_level ON batch_log(log_level);
CREATE INDEX idx_batch_log_log_time ON batch_log(log_time);
CREATE INDEX idx_batch_log_logger_name ON batch_log(logger_name);

-- 外鍵約束
ALTER TABLE batch_execution 
ADD CONSTRAINT fk_batch_execution_job_name 
FOREIGN KEY (job_name) REFERENCES batch_job_info(job_name);

-- -- 插入實際的批次作業
-- INSERT INTO batch_job_info (job_id, job_name, display_name, description, status, is_scheduled, enabled, category, estimated_duration) VALUES
-- ('get-employee-job', 'getEmployeeJob', 'GET_EMPLOYEE_JOB', '個法人信託行員檔拆解與寫入', 'IDLE', false, true, '資料處理', 10)
-- ON CONFLICT (job_id) DO UPDATE SET
--     display_name = EXCLUDED.display_name,
--     description = EXCLUDED.description,
--     status = EXCLUDED.status,
--     is_scheduled = EXCLUDED.is_scheduled,
--     enabled = EXCLUDED.enabled,
--     category = EXCLUDED.category,
--     estimated_duration = EXCLUDED.estimated_duration;
-- 'exec-003', 'reportGenerationJob', 'step-1', 'INFO', '開始生成報表', NOW() - INTERVAL '3 hours', 'cub.ret.tru.batch.job.ReportJob', 'batch-thread-3');