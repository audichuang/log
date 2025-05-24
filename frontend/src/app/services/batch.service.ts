import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { environment } from '../../environments/environment';
import { BatchLog, LogFilter, LogStats, BatchJob, BatchExecution } from '../models/batch-log.model';

@Injectable({
    providedIn: 'root'
})
export class BatchService {
    private readonly apiUrl = environment.apiUrl;

    constructor(private http: HttpClient) { }

    /**
     * 獲取所有批次作業列表
     */
    getBatchJobs(): Observable<BatchJob[]> {
        // 暫時使用模擬數據，之後可以替換為真實 API
        const mockJobs: BatchJob[] = [
            {
                id: 'get-employee-job',
                name: 'GET_EMPLOYEE_JOB',
                displayName: '員工資料處理',
                description: '從外部系統同步員工基本資料，包含薪資和部門信息',
                status: 'IDLE',
                lastExecutionTime: '2024-05-24T08:30:00',
                lastExecutionId: 'exec-001',
                isScheduled: true,
                enabled: true,
                category: '資料同步',
                estimatedDuration: 15
            },
            {
                id: 'payroll-calculation-job',
                name: 'PAYROLL_CALCULATION_JOB',
                displayName: '薪資計算',
                description: '執行月度薪資計算，包含加班費、獎金和扣除項目',
                status: 'COMPLETED',
                lastExecutionTime: '2024-05-23T23:45:00',
                lastExecutionId: 'exec-002',
                nextScheduledTime: '2024-05-31T23:45:00',
                isScheduled: true,
                enabled: true,
                category: '財務',
                estimatedDuration: 45
            },
            {
                id: 'data-backup-job',
                name: 'DATA_BACKUP_JOB',
                displayName: '資料備份',
                description: '每日備份重要業務資料到遠端儲存系統',
                status: 'RUNNING',
                lastExecutionTime: '2024-05-24T02:00:00',
                lastExecutionId: 'exec-003',
                nextScheduledTime: '2024-05-25T02:00:00',
                isScheduled: true,
                enabled: true,
                category: '系統維護',
                estimatedDuration: 30
            },
            {
                id: 'report-generation-job',
                name: 'REPORT_GENERATION_JOB',
                displayName: '報表生成',
                description: '生成各種業務報表，包含銷售、財務和人力資源報表',
                status: 'FAILED',
                lastExecutionTime: '2024-05-24T06:00:00',
                lastExecutionId: 'exec-004',
                nextScheduledTime: '2024-05-24T18:00:00',
                isScheduled: true,
                enabled: false,
                category: '報表',
                estimatedDuration: 60
            },
            {
                id: 'email-notification-job',
                name: 'EMAIL_NOTIFICATION_JOB',
                displayName: '郵件通知',
                description: '發送系統通知郵件和定期業務提醒',
                status: 'IDLE',
                isScheduled: false,
                enabled: true,
                category: '通知',
                estimatedDuration: 5
            }
        ];

        return of(mockJobs);
        // 真實 API 調用：
        // return this.http.get<BatchJob[]>(`${this.apiUrl}/batch/jobs`);
    }

    /**
     * 執行特定批次作業
     */
    runBatchJob(jobId: string): Observable<BatchExecution> {
        // 暫時返回模擬數據
        const execution: BatchExecution = {
            executionId: 'exec-' + Date.now(),
            jobName: jobId,
            status: 'RUNNING',
            startTime: new Date().toISOString(),
            message: `批次作業 ${jobId} 已成功啟動`
        };

        return of(execution);
        // 真實 API 調用：
        // return this.http.post<BatchExecution>(`${this.apiUrl}/batch/jobs/${jobId}/run`, {});
    }

    /**
     * 獲取批次作業執行狀態
     */
    getBatchJobStatus(executionId: string): Observable<BatchExecution> {
        // 真實 API 調用：
        return this.http.get<BatchExecution>(`${this.apiUrl}/batch/executions/${executionId}`);
    }

    /**
     * 停止批次作業
     */
    stopBatchJob(executionId: string): Observable<string> {
        return this.http.post<string>(`${this.apiUrl}/batch/executions/${executionId}/stop`, {}, {
            responseType: 'text' as 'json'
        });
    }

    /**
     * 根據執行代號查詢日誌
     */
    getLogsByExecutionId(executionId: string): Observable<BatchLog[]> {
        // 暫時返回模擬日誌數據
        const mockLogs: BatchLog[] = this.generateMockLogs(executionId);
        return of(mockLogs);
        // 真實 API 調用：
        // return this.http.get<BatchLog[]>(`${this.apiUrl}/batch/logs/execution/${executionId}`);
    }

    /**
     * 根據作業名稱查詢日誌
     */
    getLogsByJobName(jobName: string): Observable<BatchLog[]> {
        // 暫時返回模擬日誌數據
        const mockLogs: BatchLog[] = this.generateMockLogs(`exec-${Date.now()}`);
        return of(mockLogs);
        // 真實 API 調用：
        // return this.http.get<BatchLog[]>(`${this.apiUrl}/batch/logs/job/${jobName}`);
    }

    /**
     * 查詢錯誤日誌
     */
    getErrorLogs(executionId: string): Observable<BatchLog[]> {
        return this.http.get<BatchLog[]>(`${this.apiUrl}/batch/logs/errors/${executionId}`);
    }

    /**
     * 根據時間範圍查詢日誌
     */
    getLogsByTimeRange(startTime: string, endTime: string): Observable<BatchLog[]> {
        // 暫時返回模擬日誌數據
        const mockLogs: BatchLog[] = this.generateMockLogs(`exec-${Date.now()}`);
        return of(mockLogs);
        // 真實 API 調用：
        // const params = new HttpParams()
        //     .set('startTime', startTime)
        //     .set('endTime', endTime);
        // return this.http.get<BatchLog[]>(`${this.apiUrl}/batch/logs/time-range`, { params });
    }

    /**
     * 查詢日誌統計
     */
    getLogStats(executionId: string): Observable<any[]> {
        return this.http.get<any[]>(`${this.apiUrl}/batch/logs/stats/${executionId}`);
    }

    /**
     * 清理舊日誌
     */
    cleanupOldLogs(retentionDays: number = 30): Observable<string> {
        const params = new HttpParams().set('retentionDays', retentionDays.toString());
        return this.http.delete<string>(`${this.apiUrl}/batch/logs/cleanup`, {
            params,
            responseType: 'text' as 'json'
        });
    }

    /**
     * 生成模擬日誌數據
     */
    private generateMockLogs(executionId: string): BatchLog[] {
        const logs: BatchLog[] = [];
        const logLevels = ['DEBUG', 'INFO', 'WARN', 'ERROR'];
        const messages = [
            '批次作業開始執行',
            '正在處理資料...',
            '連接到資料庫',
            '查詢員工資料',
            '處理員工記錄',
            '更新薪資資訊',
            '發送通知郵件',
            '備份完成',
            '批次作業執行完成',
            '清理暫存資料',
            '網路連接異常，重新嘗試',
            '資料驗證失敗',
            '記憶體使用率過高',
            '檔案寫入錯誤'
        ];

        for (let i = 0; i < 50; i++) {
            const level = logLevels[Math.floor(Math.random() * logLevels.length)];
            const messageIndex = Math.floor(Math.random() * messages.length);
            const now = new Date();
            const logTime = new Date(now.getTime() - Math.random() * 24 * 60 * 60 * 1000);

            logs.push({
                id: i + 1,
                executionId: executionId,
                jobName: 'SAMPLE_JOB',
                stepName: `step-${Math.floor(i / 10) + 1}`,
                logLevel: level,
                message: messages[messageIndex] + ` (記錄 ${i + 1})`,
                exceptionStack: level === 'ERROR' ? this.generateStackTrace() : undefined,
                logTime: logTime.toISOString(),
                loggerName: 'cub.ret.tru.batch.job.SampleJob',
                threadName: `batch-thread-${Math.floor(Math.random() * 3) + 1}`,
                additionalInfo: level === 'WARN' ? '效能警告' : undefined,
                createdAt: logTime.toISOString()
            });
        }

        return logs.sort((a, b) => new Date(b.logTime).getTime() - new Date(a.logTime).getTime());
    }

    private generateStackTrace(): string {
        return `java.lang.RuntimeException: 處理過程中發生錯誤
    at cub.ret.tru.batch.job.SampleJob.execute(SampleJob.java:45)
    at cub.ret.tru.batch.listener.JobListener.beforeJob(JobListener.java:23)
    at org.springframework.batch.core.job.SimpleJob.doExecute(SimpleJob.java:144)
    at org.springframework.batch.core.job.AbstractJob.execute(AbstractJob.java:319)
    at org.springframework.batch.core.launch.support.SimpleJobLauncher.run(SimpleJobLauncher.java:147)
Caused by: java.sql.SQLException: 資料庫連接逾時
    at com.mysql.cj.jdbc.ConnectionImpl.prepareStatement(ConnectionImpl.java:1234)
    ... 5 more`;
    }
} 