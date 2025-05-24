import { Component, OnInit, OnDestroy } from '@angular/core';
import { BatchService } from '../../services/batch.service';
import { OptimizedLogService } from '../../services/optimized-log.service';
import { 
    BatchLog, 
    LogQueryCriteria, 
    LogStatsResponse 
} from '../../models/batch-log.model';
import { Subscription, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, takeUntil, tap } from 'rxjs/operators';

@Component({
    selector: 'app-log-viewer',
    templateUrl: './log-viewer.component.html',
    styleUrls: ['./log-viewer.component.scss']
})
export class LogViewerComponent implements OnInit, OnDestroy {
    logs: BatchLog[] = [];
    filteredLogs: BatchLog[] = [];
    isLoading = false;
    error = '';
    
    // 查詢條件
    currentCriteria: LogQueryCriteria = {
        queryType: 'HISTORICAL',
        sortDirection: 'DESC',
        limit: 100
    };

    // SSE 連線相關
    isStreaming = false;
    connectionStatus = 'disconnected';

    // 分頁相關
    currentPage = 1;
    pageSize = 50;
    totalPages = 0;

    // 排序相關
    sortField = 'logTime';
    sortDirection: 'asc' | 'desc' = 'desc';

    // 統計資訊
    logStats: LogStatsResponse | null = null;
    showStats = false;

    // 為模板提供 Math 對象訪問
    Math = Math;
    Object = Object;

    // RxJS響應式管理
    private readonly criteriaChange$ = new Subject<LogQueryCriteria>();
    private readonly destroy$ = new Subject<void>();

    constructor(
        private batchService: BatchService,
        private optimizedLogService: OptimizedLogService
    ) {
        console.log('LogViewerComponent 初始化，使用優化模式');
    }

    ngOnInit(): void {
        this.setupReactiveStreams();
        this.subscribeToConnectionStatus();
        this.loadInitialLogs();
    }

    ngOnDestroy(): void {
        // 發送銷毀信號
        this.destroy$.next();
        this.destroy$.complete();
        
        // 確保斷開所有連接
        this.optimizedLogService.disconnectLogStream();
        
        console.log('LogViewerComponent 已銷毀，所有連線已清理');
    }

    /**
     * 設置響應式查詢流，統一管理所有查詢條件變化
     */
    private setupReactiveStreams(): void {
        this.criteriaChange$.pipe(
            distinctUntilChanged((prev, curr) => JSON.stringify(prev) === JSON.stringify(curr)),
            debounceTime(300), // 300ms延遲，避免頻繁查詢
            tap(() => {
                console.log('查詢條件已變化，準備重新連接SSE');
                // 確保先斷開舊連接
                this.optimizedLogService.disconnectLogStream();
            }),
            switchMap(() => this.createOptimizedSSEConnection()),
            takeUntil(this.destroy$)
        ).subscribe({
            next: (logs) => {
                console.log('收到響應式SSE查詢結果:', logs.length, '條日誌');
                this.mergeNewLogs(logs);
                this.applyFilter();
                this.isLoading = false;
                this.error = '';
            },
            error: (error) => {
                console.error('響應式SSE查詢錯誤:', error);
                this.error = `SSE連線錯誤: ${error.message || error}`;
                this.isLoading = false;
                this.isStreaming = false;
            }
        });
    }

    /**
     * 建立優化的SSE連接
     */
    private createOptimizedSSEConnection() {
        if (!this.isStreaming) {
            return this.optimizedLogService.queryLogs(this.currentCriteria);
        }

        this.isLoading = true;
        console.log('建立新的SSE連接');
        return this.optimizedLogService.streamLogs(this.currentCriteria);
    }

    /**
     * 訂閱連線狀態變更
     */
    private subscribeToConnectionStatus(): void {
        this.optimizedLogService.getConnectionStatus()
            .pipe(takeUntil(this.destroy$))
            .subscribe(status => {
                this.connectionStatus = status;
                console.log('SSE連線狀態變更:', status);
            });
    }

    /**
     * 開始串流日誌（響應式版本）
     */
    startStreaming(): void {
        if (this.isStreaming) {
            return;
        }

        console.log('開始響應式串流日誌');
        this.isStreaming = true;
        
        // 觸發響應式查詢
        this.criteriaChange$.next({ ...this.currentCriteria });
    }

    /**
     * 停止串流日誌
     */
    stopStreaming(): void {
        this.isStreaming = false;
        this.optimizedLogService.disconnectLogStream();
        console.log('已停止日誌串流');
    }

    /**
     * 切換串流狀態
     */
    toggleStreaming(): void {
        if (this.isStreaming) {
            this.stopStreaming();
        } else {
            this.startStreaming();
        }
    }

    /**
     * 載入初始日誌
     */
    loadInitialLogs(): void {
        this.isLoading = true;
        this.error = '';

        // 使用快速查詢建立預設條件
        this.currentCriteria = this.optimizedLogService.createQuickCriteria('recent');
        this.loadLogsWithCriteria();
    }

    /**
     * 使用新的查詢條件載入日誌
     */
    private loadLogsWithCriteria(): void {
        this.optimizedLogService.queryLogs(this.currentCriteria).subscribe({
            next: (logs) => {
                this.logs = logs;
                this.applyFilter();
                this.isLoading = false;
                console.log('載入了', logs.length, '筆日誌');
            },
            error: (error) => {
                this.error = `載入日誌失敗: ${error.error || error.message}`;
                this.isLoading = false;
                console.error('查詢日誌失敗:', error);
            }
        });
    }

    /**
     * 響應式查詢條件變化處理
     */
    onCriteriaChange(criteria: LogQueryCriteria): void {
        this.currentCriteria = { ...criteria };
        console.log('查詢條件已更新:', this.currentCriteria);
        
        if (this.isStreaming) {
            this.criteriaChange$.next({ ...this.currentCriteria });
        } else {
            this.loadFilteredLogs();
        }
        
        // 如果有執行ID，載入統計資訊
        if (criteria.executionId) {
            this.loadLogStats(criteria.executionId);
        } else {
            this.logStats = null;
        }
    }

    /**
     * 載入過濾後的日誌
     */
    loadFilteredLogs(): void {
        this.isLoading = true;
        this.error = '';
        this.loadLogsWithCriteria();
    }

    /**
     * 載入日誌統計資訊
     */
    private loadLogStats(executionId: string): void {
        this.optimizedLogService.getLogStats(executionId).subscribe({
            next: (stats) => {
                this.logStats = stats;
                console.log('載入統計資訊:', stats);
            },
            error: (error) => {
                console.warn('載入統計資訊失敗:', error);
                this.logStats = null;
            }
        });
    }

    /**
     * 快速操作：顯示錯誤日誌
     */
    showErrorsOnly(): void {
        this.currentCriteria = this.optimizedLogService.createQuickCriteria('errors', this.currentCriteria.jobName);
        this.loadFilteredLogs();
    }

    /**
     * 快速操作：顯示警告和錯誤
     */
    showWarningsAndErrors(): void {
        this.currentCriteria = this.optimizedLogService.createQuickCriteria('warnings', this.currentCriteria.jobName);
        this.loadFilteredLogs();
    }

    /**
     * 切換統計顯示
     */
    toggleStats(): void {
        this.showStats = !this.showStats;
    }

    // 原有的方法保持不變
    applyFilter(): void {
        let filtered = [...this.logs];

        // 關鍵字搜索（如果後端沒有完全處理）
        if (this.currentCriteria.keyword && this.currentCriteria.keyword.trim()) {
            const keyword = this.currentCriteria.keyword.toLowerCase();
            filtered = filtered.filter(log =>
                log.message.toLowerCase().includes(keyword) ||
                log.loggerName.toLowerCase().includes(keyword)
            );
        }

        // 排序
        filtered.sort((a, b) => {
            const aValue = this.getFieldValue(a, this.sortField);
            const bValue = this.getFieldValue(b, this.sortField);

            if (aValue < bValue) return this.sortDirection === 'asc' ? -1 : 1;
            if (aValue > bValue) return this.sortDirection === 'asc' ? 1 : -1;
            return 0;
        });

        this.filteredLogs = filtered;
        this.totalPages = Math.ceil(this.filteredLogs.length / this.pageSize);
        this.currentPage = 1;
    }

    private getFieldValue(log: BatchLog, field: string): any {
        switch (field) {
            case 'logTime':
                return new Date(log.logTime);
            case 'logLevel':
                return log.logLevel;
            default:
                return (log as any)[field];
        }
    }

    sort(field: string): void {
        if (this.sortField === field) {
            this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
        } else {
            this.sortField = field;
            this.sortDirection = 'desc';
        }
        this.applyFilter();
    }

    get paginatedLogs(): BatchLog[] {
        const start = (this.currentPage - 1) * this.pageSize;
        const end = start + this.pageSize;
        return this.filteredLogs.slice(start, end);
    }

    goToPage(page: number): void {
        if (page >= 1 && page <= this.totalPages) {
            this.currentPage = page;
        }
    }

    getPageNumbers(): number[] {
        const pages = [];
        const maxVisible = 5;
        let start = Math.max(1, this.currentPage - Math.floor(maxVisible / 2));
        let end = Math.min(this.totalPages, start + maxVisible - 1);

        if (end - start + 1 < maxVisible) {
            start = Math.max(1, end - maxVisible + 1);
        }

        for (let i = start; i <= end; i++) {
            pages.push(i);
        }
        return pages;
    }

    getLogLevelClass(level: string): string {
        switch (level) {
            case 'ERROR':
                return 'log-error';
            case 'WARN':
                return 'log-warning';
            case 'INFO':
                return 'log-info';
            case 'DEBUG':
                return 'log-debug';
            default:
                return 'log-default';
        }
    }

    formatDateTime(dateTime: string): string {
        return new Date(dateTime).toLocaleString('zh-TW');
    }

    clearError(): void {
        this.error = '';
    }

    /**
     * 合併新的日誌資料
     */
    private mergeNewLogs(newLogs: BatchLog[]): void {
        if (!newLogs || newLogs.length === 0) {
            return;
        }

        // 避免重複的日誌
        const existingIds = new Set(this.logs.map(log => log.id));
        const uniqueNewLogs = newLogs.filter(log => !existingIds.has(log.id));
        
        if (uniqueNewLogs.length > 0) {
            // 根據排序方向決定插入位置
            if (this.currentCriteria.sortDirection === 'DESC') {
                this.logs = [...uniqueNewLogs, ...this.logs];
            } else {
                this.logs = [...this.logs, ...uniqueNewLogs];
            }
            console.log('合併了', uniqueNewLogs.length, '筆新日誌');
        }
    }
} 