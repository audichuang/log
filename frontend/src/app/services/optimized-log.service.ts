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
    private readonly MAX_LOGS_IN_MEMORY = 500; // 最大內存中的日誌數

    private pollingInterval: any = null;
    private logsSubject = new BehaviorSubject<BatchLog[]>([]); // 🔥 改用 BehaviorSubject，保持狀態
    private connectionStatus = new BehaviorSubject<string>('disconnected');

    private lastLogId: number | undefined = undefined; // 🔥 改名為 lastLogId，但發送時用 lastId
    private currentCriteria: LogQueryCriteria | null = null;
    private accumulatedLogs: BatchLog[] = []; // 🔥 用於累積日誌

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

        // 🔥 重要：前端 lastLogId -> 後端 lastId
        if (criteria.lastId) params = params.set('lastId', criteria.lastId.toString());

        return this.http.get<BatchLog[]>(`${this.apiUrl}/batch/optimized-logs`, { params });
    }

    /**
     * POST 方式的複雜查詢
     */
    queryLogsPost(criteria: LogQueryCriteria): Observable<BatchLog[]> {
        return this.http.post<BatchLog[]>(`${this.apiUrl}/batch/optimized-logs/query`, criteria);
    }

    /**
     * 開始輪詢日誌 - 改進版本
     */
    startPollingLogs(criteria?: Partial<LogQueryCriteria>): Observable<BatchLog[]> {
        console.log('🚀 開始輪詢日誌，條件:', criteria);

        // 停止現有輪詢並重置狀態
        this.stopPollingLogs();
        this.resetPollingState();

        // 建立查詢條件
        this.currentCriteria = this.buildFullCriteria(criteria);
        this.connectionStatus.next('connecting');

        // 立即執行首次查詢
        this.executeInitialQuery();

        // 開始定時輪詢增量數據
        this.startIncrementalPolling();

        return this.logsSubject.asObservable();
    }

    /**
     * 重置輪詢狀態
     */
    private resetPollingState(): void {
        this.lastLogId = undefined;
        this.accumulatedLogs = [];
        this.logsSubject.next([]);
    }

    /**
     * 執行首次查詢
     */
    private executeInitialQuery(): void {
        if (!this.currentCriteria) return;

        const initialCriteria: LogQueryCriteria = {
            ...this.currentCriteria,
            sortDirection: 'DESC', // 首次查詢用降序，獲取最新日誌
            limit: 100
        };

        console.log('📥 執行首次查詢:', initialCriteria);

        this.queryLogsPost(initialCriteria).subscribe({
            next: (logs) => {
                if (logs && logs.length > 0) {
                    console.log('✅ 首次查詢成功，獲取', logs.length, '條日誌');

                    // 🔥 將日誌按時間順序排列（舊到新）以便後續累積
                    this.accumulatedLogs = [...logs].reverse();

                    // 設置最後的日誌ID
                    const maxId = Math.max(...logs.map(log => log.id || 0));
                    this.lastLogId = maxId;

                    console.log('📝 設置 lastLogId:', this.lastLogId);

                    // 🔥 發送初始日誌（保持降序顯示，最新在上）
                    this.logsSubject.next([...logs]);
                    this.connectionStatus.next('connected');
                } else {
                    console.log('📭 首次查詢無日誌');
                    this.connectionStatus.next('connected');
                }
            },
            error: (error) => {
                console.error('❌ 首次查詢失敗:', error);
                this.connectionStatus.next('error');
            }
        });
    }

    /**
     * 開始增量輪詢
     */
    private startIncrementalPolling(): void {
        console.log('⏰ 開始增量輪詢，間隔 5 秒');

        this.pollingInterval = setInterval(() => {
            this.executeIncrementalQuery();
        }, 5000);
    }

    /**
     * 執行增量查詢
     */
    private executeIncrementalQuery(): void {
        if (!this.currentCriteria || this.lastLogId === undefined) {
            console.log('⏭️ 跳過增量查詢：條件不足');
            return;
        }

        const incrementalCriteria: LogQueryCriteria = {
            ...this.currentCriteria,
            lastId: this.lastLogId, // 🔥 使用 lastId 而不是 lastLogId
            sortDirection: 'ASC', // 增量查詢用升序
            limit: 50,
            // 清除時間條件，因為用ID查詢更精確
            startTime: undefined,
            endTime: undefined
        };

        console.log('🔄 執行增量查詢，lastId:', this.lastLogId);

        this.queryLogsPost(incrementalCriteria).subscribe({
            next: (newLogs) => {
                if (newLogs && newLogs.length > 0) {
                    console.log('📨 收到新日誌:', newLogs.length, '條');
                    this.handleIncrementalLogs(newLogs);
                } else {
                    console.log('📭 無新日誌');
                }
                this.connectionStatus.next('connected');
            },
            error: (error) => {
                console.error('❌ 增量查詢失敗:', error);
                this.connectionStatus.next('error');
            }
        });
    }

    /**
     * 處理增量日誌
     */
    private handleIncrementalLogs(newLogs: BatchLog[]): void {
        // 🔥 添加新日誌到累積列表的末尾（保持時間順序）
        this.accumulatedLogs.push(...newLogs);

        // 🔥 更新最後的日誌ID
        const maxId = Math.max(...newLogs.map(log => log.id || 0));
        this.lastLogId = Math.max(this.lastLogId || 0, maxId);

        console.log('📝 更新 lastLogId:', this.lastLogId);

        // 🔥 內存管理：保持最多 MAX_LOGS_IN_MEMORY 條日誌
        if (this.accumulatedLogs.length > this.MAX_LOGS_IN_MEMORY) {
            const removeCount = this.accumulatedLogs.length - this.MAX_LOGS_IN_MEMORY;
            this.accumulatedLogs.splice(0, removeCount);
            console.log('🧹 清理舊日誌:', removeCount, '條，剩餘:', this.accumulatedLogs.length);
        }

        // 🔥 發送更新的日誌列表（降序顯示，最新在上）
        const displayLogs = [...this.accumulatedLogs].reverse();
        this.logsSubject.next(displayLogs);
    }

    /**
     * 構建完整的查詢條件
     */
    private buildFullCriteria(criteria?: Partial<LogQueryCriteria>): LogQueryCriteria {
        return {
            executionId: criteria?.executionId,
            jobName: criteria?.jobName,
            stepName: criteria?.stepName,
            logLevels: criteria?.logLevels,
            keyword: criteria?.keyword,
            startTime: criteria?.startTime,
            endTime: criteria?.endTime,
            sortDirection: 'DESC',
            queryType: 'REAL_TIME',
            limit: 100,
            ...criteria
        };
    }

    /**
     * 更新輪詢過濾條件
     */
    updatePollingFilter(criteria: Partial<LogQueryCriteria>): void {
        console.log('🔄 更新輪詢過濾條件:', criteria);

        // 🔥 重新開始輪詢，因為條件變了
        this.startPollingLogs(criteria);
    }

    /**
     * 停止輪詢
     */
    stopPollingLogs(): void {
        if (this.pollingInterval) {
            console.log('⏹️ 停止日誌輪詢');
            clearInterval(this.pollingInterval);
            this.pollingInterval = null;
            this.connectionStatus.next('disconnected');
        }
    }

    /**
     * 手動刷新
     */
    refresh(): void {
        console.log('🔄 手動刷新');
        if (this.currentCriteria) {
            this.startPollingLogs(this.currentCriteria);
        }
    }

    /**
     * 清空日誌顯示
     */
    clearLogs(): void {
        console.log('🧹 清空日誌顯示');
        this.resetPollingState();
    }

    /**
     * 獲取輪詢狀態
     */
    getPollingStatus(): {
        isPolling: boolean;
        criteria: LogQueryCriteria | null;
        logCount: number;
        lastLogId?: number;
    } {
        return {
            isPolling: this.pollingInterval !== null,
            criteria: this.currentCriteria,
            logCount: this.accumulatedLogs.length,
            lastLogId: this.lastLogId
        };
    }

    /**
     * 獲取連接狀態
     */
    getConnectionStatus(): Observable<string> {
        return this.connectionStatus.asObservable();
    }

    /**
     * 檢查是否正在輪詢
     */
    isConnected(): boolean {
        return this.pollingInterval !== null;
    }

    /**
     * 斷開輪詢連接（相容性方法）
     */
    disconnectLogStream(): void {
        this.stopPollingLogs();
    }

    // 🔥 其他方法保持不變
    getLogStats(executionId: string): Observable<LogStatsResponse> {
        return this.http.get<LogStatsResponse>(`${this.apiUrl}/batch/optimized-logs/stats/${executionId}`);
    }

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