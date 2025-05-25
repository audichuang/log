export interface BatchLog {
    id: number;
    executionId: string;
    jobName: string;
    stepName: string;
    logLevel: string;
    message: string;
    exceptionStack?: string;
    logTime: string;
    loggerName: string;
    threadName: string;
    additionalInfo?: string;
    createdAt: string;
}

// 新增：優化的查詢條件接口
export interface LogQueryCriteria {
    executionId?: string;
    jobName?: string;
    stepName?: string;
    logLevels?: string[];  // 支援多個日誌級別
    keyword?: string;
    startTime?: string;
    endTime?: string;
    limit?: number;
    sortDirection?: 'ASC' | 'DESC';
    queryType?: 'REAL_TIME' | 'HISTORICAL' | 'SEARCH';
    lastId?: number;  // 用於ID增量查詢的最後日誌ID
}

// 保留舊的 LogFilter 以向後兼容，但標記為 deprecated
/** @deprecated 請使用 LogQueryCriteria */
export interface LogFilter {
    startTime?: string;
    endTime?: string;
    logLevel?: string;
    keyword?: string;
    executionId?: string;
    jobName?: string;
}

// 新增：多選日誌級別的過濾器
export interface LogFilterOptions {
    startTime?: string;
    endTime?: string;
    logLevels?: string[];  // 改為數組支持多選
    keyword?: string;
    executionId?: string;
    jobName?: string;
    stepName?: string;
}

export interface LogStats {
    level: string;
    count: number;
}

// 新增：日誌統計響應
export interface LogStatsResponse {
    totalCount: number;
    levelCounts: { [key: string]: number };
    executionId: string;
}

// 新增：SSE 連接狀態
export interface SseConnectionStatus {
    activeConnections: number;
    timestamp: number;
    serverTime: string;
}

// 新增批次作業模型
export interface BatchJob {
    id: string;
    name: string;
    displayName: string;
    description: string;
    status: 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED';
    lastExecutionTime?: string;
    lastExecutionId?: string;
    nextScheduledTime?: string;
    isScheduled: boolean;
    enabled: boolean;
    category: string;
    estimatedDuration?: number; // 預估執行時間（分鐘）
}

export interface BatchExecution {
    executionId: string;
    jobName: string;
    status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'STOPPED';
    startTime: string;
    endTime?: string;
    exitCode?: number;
    exitMessage?: string;
    message?: string;
    logs?: BatchLog[];
}

// 新增：日誌級別常數
export const LOG_LEVELS = [
    { value: 'DEBUG', label: 'DEBUG', color: '#6c757d' },
    { value: 'INFO', label: 'INFO', color: '#17a2b8' },
    { value: 'WARN', label: 'WARN', color: '#ffc107' },
    { value: 'ERROR', label: 'ERROR', color: '#dc3545' }
] as const;

export type LogLevel = typeof LOG_LEVELS[number]['value']; 