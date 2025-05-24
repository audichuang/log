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
        // 確保完全斷開舊連接
        this.disconnectLogStream();
        
        let params = new URLSearchParams();
        if (criteria) {
            if (criteria.executionId) params.append('executionId', criteria.executionId);
            if (criteria.jobName) params.append('jobName', criteria.jobName);
            if (criteria.logLevels && criteria.logLevels.length > 0) {
                // 支援多選日誌級別：每個級別作為單獨的參數
                criteria.logLevels.forEach(level => {
                    params.append('logLevels', level);
                });
            }
            if (criteria.keyword) params.append('keyword', criteria.keyword);
            if (criteria.startTime) params.append('startTime', criteria.startTime);
            if (criteria.endTime) params.append('endTime', criteria.endTime);
        }

        const url = `${this.apiUrl}/batch/optimized-logs/stream?${params.toString()}`;
        console.log('建立 SSE 連接到:', url);
        
        // 等待一小段時間確保舊連接完全關閉
        return new Observable<BatchLog[]>(observer => {
            setTimeout(() => {
                this.eventSource = new EventSource(url);
                
                this.eventSource.onopen = () => {
                    console.log('SSE 連接已建立:', url);
                    this.connectionStatus.next('connected');
                    
                    // 從URL中提取連接ID（後端生成的UUID）
                    // 注意：我們需要從SSE響應中獲取實際的connectionId
                };

                this.eventSource.addEventListener('connected', (event: any) => {
                    console.log('SSE 連接確認:', event.data);
                    this.connectionStatus.next('connected');
                    
                    // 嘗試從連接確認消息中提取連接ID
                    try {
                        // 如果後端返回JSON格式的連接信息
                        const connectionInfo = JSON.parse(event.data);
                        if (connectionInfo.connectionId) {
                            this.connectionId = connectionInfo.connectionId;
                            console.log('已獲取連接ID:', this.connectionId);
                        }
                    } catch (e) {
                        // 如果不是JSON，暫時使用URL作為標識
                        console.log('連接已建立，暫時無法獲取連接ID');
                    }
                });

                this.eventSource.addEventListener('logs', (event: any) => {
                    try {
                        const logs = JSON.parse(event.data);
                        if (Array.isArray(logs)) {
                            observer.next(logs);
                        }
                    } catch (error) {
                        console.error('解析日誌數據失敗:', error);
                        observer.error(error);
                    }
                });

                this.eventSource.onerror = (error) => {
                    console.error('SSE 連接錯誤:', error);
                    this.connectionStatus.next('error');
                    observer.error(error);
                };
                
                // 返回清理函數
                return () => {
                    console.log('Observable 被取消訂閱，執行清理');
                    this.disconnectLogStream();
                };
                
            }, 100); // 100ms 延遲確保舊連接清理完成
        });
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
            console.log('正在關閉 SSE 連接，當前狀態:', this.eventSource.readyState);
            
            // 如果有連接ID，主動通知後端關閉連接
            if (this.connectionId) {
                console.log('主動通知後端關閉連接:', this.connectionId);
                this.http.delete(`${this.apiUrl}/batch/optimized-logs/stream/${this.connectionId}`)
                    .subscribe({
                        next: (response) => {
                            console.log('後端連接已關閉:', response);
                        },
                        error: (error) => {
                            console.warn('通知後端關閉連接失敗:', error);
                        }
                    });
            }
            
            // 移除所有事件監聽器
            this.eventSource.onopen = null;
            this.eventSource.onmessage = null;
            this.eventSource.onerror = null;
            
            // 關閉連接
            this.eventSource.close();
            this.eventSource = null;
            this.connectionId = null;
            
            // 更新連接狀態
            this.connectionStatus.next('disconnected');
            console.log('SSE 連接已關閉並清理');
        } else {
            console.log('沒有活躍的 SSE 連接需要關閉');
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