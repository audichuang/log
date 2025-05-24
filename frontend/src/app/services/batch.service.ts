import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of, Subject, BehaviorSubject } from 'rxjs';
import { environment } from '../../environments/environment';
import { BatchLog, LogFilter, LogStats, BatchJob, BatchExecution } from '../models/batch-log.model';

@Injectable({
    providedIn: 'root'
})
export class BatchService {
    private readonly apiUrl = environment.apiUrl;
    private eventSource: EventSource | null = null;
    private logsSubject = new Subject<BatchLog[]>();
    private connectionStatus = new BehaviorSubject<string>('disconnected');

    constructor(private http: HttpClient) { }

    /**
     * 獲取所有批次作業列表
     */
    getBatchJobs(): Observable<BatchJob[]> {
        return this.http.get<BatchJob[]>(`${this.apiUrl}/batch/jobs`);
    }

    /**
     * 執行特定批次作業
     */
    runBatchJob(jobId: string): Observable<BatchExecution> {
        return this.http.post<BatchExecution>(`${this.apiUrl}/batch/jobs/${jobId}/run`, {});
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

    /**
     * 建立 SSE 連接來串流日誌
     */
    connectToLogStream(filter?: LogFilter): Observable<BatchLog[]> {
        this.disconnectLogStream(); // 關閉現有連接

        const params = new URLSearchParams();
        if (filter) {
            if (filter.executionId) params.append('executionId', filter.executionId);
            if (filter.jobName) params.append('jobName', filter.jobName);
            if (filter.logLevel) params.append('logLevel', filter.logLevel);
            if (filter.keyword) params.append('keyword', filter.keyword);
            if (filter.startTime) params.append('startTime', filter.startTime);
            if (filter.endTime) params.append('endTime', filter.endTime);
        }

        const url = `${this.apiUrl}/batch/logs/stream?${params.toString()}`;
        this.eventSource = new EventSource(url);

        this.eventSource.onopen = () => {
            console.log('SSE 連接已建立');
            this.connectionStatus.next('connected');
        };

        this.eventSource.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (Array.isArray(data)) {
                    this.logsSubject.next(data);
                }
            } catch (error) {
                console.error('解析 SSE 數據失敗:', error);
            }
        };

        this.eventSource.addEventListener('logs', (event: any) => {
            try {
                const logs = JSON.parse(event.data);
                this.logsSubject.next(logs);
            } catch (error) {
                console.error('解析日誌數據失敗:', error);
            }
        });

        this.eventSource.addEventListener('connected', (event: any) => {
            console.log('SSE 連接確認:', event.data);
            this.connectionStatus.next('connected');
        });

        this.eventSource.onerror = (error) => {
            console.error('SSE 連接錯誤:', error);
            this.connectionStatus.next('error');
            // 5秒後重新連接
            setTimeout(() => {
                if (this.eventSource?.readyState === EventSource.CLOSED) {
                    this.connectToLogStream(filter);
                }
            }, 5000);
        };

        return this.logsSubject.asObservable();
    }

    /**
     * 斷開 SSE 連接
     */
    disconnectLogStream(): void {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
            this.connectionStatus.next('disconnected');
            console.log('SSE 連接已關閉');
        }
    }

    /**
     * 獲取連接狀態
     */
    getConnectionStatus(): Observable<string> {
        return this.connectionStatus.asObservable();
    }

    /**
     * 檢查是否已連接
     */
    isConnected(): boolean {
        return this.eventSource?.readyState === EventSource.OPEN;
    }

    /**
     * 更新過濾條件
     */
    updateLogFilter(filter: LogFilter): void {
        // 重新建立連接，使用新的過濾條件
        this.connectToLogStream(filter);
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