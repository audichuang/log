import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subject, BehaviorSubject } from 'rxjs';
import { environment } from '../../environments/environment';
import { 
    BatchLog, 
    LogQueryCriteria, 
    LogStatsResponse, 
    SseConnectionStatus,
    LogFilterOptions 
} from '../models/batch-log.model';

@Injectable({
    providedIn: 'root'
})
export class OptimizedLogService {
    private readonly apiUrl = environment.apiUrl;
    private eventSource: EventSource | null = null;
    private connectionId: string | null = null;
    private logsSubject = new Subject<BatchLog[]>();
    private connectionStatus = new BehaviorSubject<string>('disconnected');

    constructor(private http: HttpClient) { }

    /**
     * 統一的日誌查詢方法 (GET)
     */
    queryLogs(criteria: LogQueryCriteria): Observable<BatchLog[]> {
        let params = new HttpParams();
        
        if (criteria.executionId) params = params.set('executionId', criteria.executionId);
        if (criteria.jobName) params = params.set('jobName', criteria.jobName);
        if (criteria.stepName) params = params.set('stepName', criteria.stepName);
        if (criteria.logLevels && criteria.logLevels.length > 0) {
            criteria.logLevels.forEach(level => {
                params = params.append('logLevels', level);
            });
        }
        if (criteria.keyword) params = params.set('keyword', criteria.keyword);
        if (criteria.startTime) params = params.set('startTime', criteria.startTime);
        if (criteria.endTime) params = params.set('endTime', criteria.endTime);
        if (criteria.limit) params = params.set('limit', criteria.limit.toString());
        if (criteria.sortDirection) params = params.set('sortDirection', criteria.sortDirection);
        if (criteria.queryType) params = params.set('queryType', criteria.queryType);

        return this.http.get<BatchLog[]>(`${this.apiUrl}/batch/optimized-logs`, { params });
    }

    /**
     * POST 方式的複雜查詢
     */
    queryLogsPost(criteria: LogQueryCriteria): Observable<BatchLog[]> {
        return this.http.post<BatchLog[]>(`${this.apiUrl}/batch/optimized-logs/query`, criteria);
    }

    /**
     * 建立 SSE 實時串流連接
     */
    streamLogs(criteria?: Partial<LogQueryCriteria>): Observable<BatchLog[]> {
        this.disconnectLogStream();

        let params = new URLSearchParams();
        if (criteria) {
            if (criteria.executionId) params.append('executionId', criteria.executionId);
            if (criteria.jobName) params.append('jobName', criteria.jobName);
            if (criteria.logLevels && criteria.logLevels.length > 0) {
                // 對於 SSE，只取第一個日誌級別（後端參數限制）
                params.append('logLevel', criteria.logLevels[0]);
            }
            if (criteria.keyword) params.append('keyword', criteria.keyword);
            if (criteria.startTime) params.append('startTime', criteria.startTime);
            if (criteria.endTime) params.append('endTime', criteria.endTime);
        }

        const url = `${this.apiUrl}/batch/optimized-logs/stream?${params.toString()}`;
        this.eventSource = new EventSource(url);

        this.eventSource.onopen = () => {
            console.log('SSE 連接已建立');
            this.connectionStatus.next('connected');
        };

        this.eventSource.addEventListener('connected', (event: any) => {
            console.log('SSE 連接確認:', event.data);
            this.connectionStatus.next('connected');
        });

        this.eventSource.addEventListener('logs', (event: any) => {
            try {
                const logs = JSON.parse(event.data);
                if (Array.isArray(logs)) {
                    this.logsSubject.next(logs);
                }
            } catch (error) {
                console.error('解析日誌數據失敗:', error);
            }
        });

        this.eventSource.onerror = (error) => {
            console.error('SSE 連接錯誤:', error);
            this.connectionStatus.next('error');
            
            // 5秒後重新連接
            setTimeout(() => {
                if (this.eventSource?.readyState === EventSource.CLOSED) {
                    this.streamLogs(criteria);
                }
            }, 5000);
        };

        return this.logsSubject.asObservable();
    }

    /**
     * 更新串流過濾條件
     */
    updateStreamFilter(criteria: LogQueryCriteria): Observable<string> {
        if (!this.connectionId) {
            throw new Error('沒有活躍的串流連接');
        }
        
        return this.http.put<string>(
            `${this.apiUrl}/batch/optimized-logs/stream/${this.connectionId}/filter`, 
            criteria,
            { responseType: 'text' as 'json' }
        );
    }

    /**
     * 關閉串流連接
     */
    closeStream(): Observable<string> {
        if (!this.connectionId) {
            return new Observable(observer => {
                observer.next('沒有活躍的連接');
                observer.complete();
            });
        }

        return this.http.delete<string>(
            `${this.apiUrl}/batch/optimized-logs/stream/${this.connectionId}`,
            { responseType: 'text' as 'json' }
        );
    }

    /**
     * 獲取串流狀態
     */
    getStreamStatus(): Observable<SseConnectionStatus> {
        return this.http.get<SseConnectionStatus>(`${this.apiUrl}/batch/optimized-logs/stream/status`);
    }

    /**
     * 獲取日誌統計
     */
    getLogStats(executionId: string): Observable<LogStatsResponse> {
        return this.http.get<LogStatsResponse>(`${this.apiUrl}/batch/optimized-logs/stats/${executionId}`);
    }

    /**
     * 斷開 SSE 連接
     */
    disconnectLogStream(): void {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
            this.connectionId = null;
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
     * 將舊的 LogFilterOptions 轉換為新的 LogQueryCriteria
     */
    convertFilterToCriteria(filter: LogFilterOptions): LogQueryCriteria {
        return {
            executionId: filter.executionId,
            jobName: filter.jobName,
            stepName: filter.stepName,
            logLevels: filter.logLevels,
            keyword: filter.keyword,
            startTime: filter.startTime,
            endTime: filter.endTime,
            sortDirection: 'DESC',
            queryType: 'HISTORICAL',
            limit: 100
        };
    }

    /**
     * 建立快速查詢條件
     */
    createQuickCriteria(type: 'recent' | 'errors' | 'warnings', jobName?: string): LogQueryCriteria {
        const now = new Date();
        const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);

        const baseCriteria: LogQueryCriteria = {
            startTime: yesterday.toISOString(),
            endTime: now.toISOString(),
            sortDirection: 'DESC',
            queryType: 'HISTORICAL',
            limit: 100
        };

        if (jobName) {
            baseCriteria.jobName = jobName;
        }

        switch (type) {
            case 'recent':
                return baseCriteria;
            case 'errors':
                return { ...baseCriteria, logLevels: ['ERROR'] };
            case 'warnings':
                return { ...baseCriteria, logLevels: ['WARN', 'ERROR'] };
            default:
                return baseCriteria;
        }
    }
} 