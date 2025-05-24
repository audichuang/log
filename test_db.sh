#!/bin/bash

# 資料庫連接參數
DB_HOST="192.168.31.247"
DB_PORT="5444"
DB_USER="root"
DB_NAME="postgres"
DB_PASSWORD="VZq9rWbC3oJYFYdDrjT6edewVHQEKNCBWPDnyqxKyzMTE3CoozBrWnYsi6KkpwKujcFKDytQCrxhTbcxsAB2vswcVgQc9ieYvtpP"

# 設定密碼環境變數
export PGPASSWORD="$DB_PASSWORD"

echo "正在查詢 batch_log 表格記錄數量..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT COUNT(*) as total_records FROM batch_log;"

echo ""
echo "正在查詢最新的 5 條記錄..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT id, execution_id, job_name, log_level, message, log_time FROM batch_log ORDER BY id DESC LIMIT 5;"

echo ""
echo "正在查詢包含 TEST_ 執行代號的記錄..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT id, execution_id, job_name, log_level, message, log_time FROM batch_log WHERE execution_id LIKE 'TEST_%' ORDER BY id;"

echo ""
echo "正在查詢最新的批次作業完整記錄..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "
SELECT id, execution_id, job_name, step_name, log_level, message, 
       TO_CHAR(log_time, 'YYYY-MM-DD HH24:MI:SS.MS') as formatted_time
FROM batch_log 
WHERE execution_id LIKE '202505240629%' 
ORDER BY id;
"

echo ""
echo "正在查詢批次作業統計..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "
SELECT execution_id, job_name, 
       COUNT(*) as total_logs,
       COUNT(CASE WHEN log_level = 'INFO' THEN 1 END) as info_count,
       COUNT(CASE WHEN log_level = 'WARN' THEN 1 END) as warn_count,
       COUNT(CASE WHEN log_level = 'ERROR' THEN 1 END) as error_count,
       COUNT(CASE WHEN log_level = 'DEBUG' THEN 1 END) as debug_count,
       MIN(log_time) as start_time,
       MAX(log_time) as end_time
FROM batch_log 
WHERE execution_id LIKE '202505240629%'
GROUP BY execution_id, job_name;
"

echo ""
echo "正在查詢批量測試統計..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "
SELECT execution_id, job_name, 
       COUNT(*) as total_logs,
       COUNT(CASE WHEN log_level = 'INFO' THEN 1 END) as info_count,
       COUNT(CASE WHEN log_level = 'WARN' THEN 1 END) as warn_count,
       COUNT(CASE WHEN log_level = 'ERROR' THEN 1 END) as error_count,
       MIN(log_time) as start_time,
       MAX(log_time) as end_time
FROM batch_log 
WHERE execution_id LIKE 'BATCH_TEST_%'
GROUP BY execution_id, job_name
ORDER BY execution_id DESC
LIMIT 3;
" 