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

-- 插入實際的批次作業
INSERT INTO batch_job_info (job_id, job_name, display_name, description, status, is_scheduled, enabled, category, estimated_duration) VALUES
('get-employee-job', 'getEmployeeJob', 'GET_EMPLOYEE_JOB', '個法人信託行員檔拆解與寫入', 'IDLE', false, true, '資料處理', 10)
ON CONFLICT (job_id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    is_scheduled = EXCLUDED.is_scheduled,
    enabled = EXCLUDED.enabled,
    category = EXCLUDED.category,
    estimated_duration = EXCLUDED.estimated_duration;

-- 插入一些示例日誌數據
INSERT INTO batch_log (execution_id, job_name, step_name, log_level, message, log_time, logger_name, thread_name) VALUES
('exec-001', 'getEmployeeJob', 'step-1', 'INFO', '批次作業開始執行', NOW() - INTERVAL '1 hour', 'cub.ret.tru.batch.job.GetEmployeeJob', 'batch-thread-1'),
('exec-001', 'getEmployeeJob', 'step-1', 'INFO', '正在處理員工資料...', NOW() - INTERVAL '50 minutes', 'cub.ret.tru.batch.job.GetEmployeeJob', 'batch-thread-1'),
('exec-001', 'getEmployeeJob', 'step-1', 'DEBUG', '連接到資料庫', NOW() - INTERVAL '45 minutes', 'cub.ret.tru.batch.job.GetEmployeeJob', 'batch-thread-1'),
('exec-001', 'getEmployeeJob', 'step-2', 'INFO', '查詢員工資料', NOW() - INTERVAL '40 minutes', 'cub.ret.tru.batch.job.GetEmployeeJob', 'batch-thread-1'),
('exec-001', 'getEmployeeJob', 'step-2', 'WARN', '部分員工資料缺失', NOW() - INTERVAL '35 minutes', 'cub.ret.tru.batch.job.GetEmployeeJob', 'batch-thread-1'),
('exec-001', 'getEmployeeJob', 'step-3', 'INFO', '處理員工記錄', NOW() - INTERVAL '30 minutes', 'cub.ret.tru.batch.job.GetEmployeeJob', 'batch-thread-1'),
('exec-001', 'getEmployeeJob', 'step-3', 'INFO', '批次作業執行完成', NOW() - INTERVAL '25 minutes', 'cub.ret.tru.batch.job.GetEmployeeJob', 'batch-thread-1'),
('exec-002', 'payrollCalculationJob', 'step-1', 'INFO', '薪資計算開始', NOW() - INTERVAL '2 hours', 'cub.ret.tru.batch.job.PayrollJob', 'batch-thread-2'),
('exec-002', 'payrollCalculationJob', 'step-1', 'ERROR', '薪資計算過程中發生錯誤', NOW() - INTERVAL '1 hour 30 minutes', 'cub.ret.tru.batch.job.PayrollJob', 'batch-thread-2'),
('exec-003', 'reportGenerationJob', 'step-1', 'INFO', '開始生成報表', NOW() - INTERVAL '3 hours', 'cub.ret.tru.batch.job.ReportJob', 'batch-thread-3'); 