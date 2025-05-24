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

export interface LogFilter {
    startTime?: string;
    endTime?: string;
    logLevel?: string;
    keyword?: string;
    executionId?: string;
    jobName?: string;
}

export interface LogStats {
    level: string;
    count: number;
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
    status: 'RUNNING' | 'COMPLETED' | 'FAILED';
    startTime: string;
    endTime?: string;
    message?: string;
    logs?: BatchLog[];
} 