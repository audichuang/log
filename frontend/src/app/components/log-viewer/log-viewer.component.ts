import { Component, OnInit, OnDestroy } from '@angular/core';
import { BatchService } from '../../services/batch.service';
import { OptimizedLogService } from '../../services/optimized-log.service';
import { 
    BatchLog, 
    LogFilter, 
    LogQueryCriteria, 
    LogStatsResponse 
} from '../../models/batch-log.model';
import { Subscription } from 'rxjs';

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
    
    // 新的查詢條件
    currentCriteria: LogQueryCriteria = {
        queryType: 'HISTORICAL',
        sortDirection: 'DESC',
        limit: 100
    };
    
    // 保持向後兼容
    currentFilter: LogFilter = {};

    // SSE 連線相關
    private sseSubscription?: Subscription;
    private connectionStatusSubscription?: Subscription;
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

    // 查詢模式：'legacy' 或 'optimized'
    queryMode: 'legacy' | 'optimized' = 'optimized';

    // 為模板提供 Math 對象訪問
    Math = Math;
    Object = Object;

    constructor(
        private batchService: BatchService,
        private optimizedLogService: OptimizedLogService
    ) {
        console.log('LogViewerComponent 初始化，預設使用優化模式');
    }

    ngOnInit(): void {
        this.loadInitialLogs();
        this.subscribeToConnectionStatus();
    }

    ngOnDestroy(): void {
        // 組件銷毀時主動斷開SSE連線
        this.disconnectStream();
        
        // 取消所有訂閱
        if (this.sseSubscription) {
            this.sseSubscription.unsubscribe();
        }
        if (this.connectionStatusSubscription) {
            this.connectionStatusSubscription.unsubscribe();
        }
        
        console.log('LogViewerComponent 已銷毀，SSE連線已清理');
    }

    /**
     * 訂閱連線狀態變更
     */
    private subscribeToConnectionStatus(): void {
        if (this.queryMode === 'optimized') {
            this.connectionStatusSubscription = this.optimizedLogService.getConnectionStatus().subscribe(
                status => {
                    this.connectionStatus = status;
                    console.log('SSE連線狀態變更:', status);
                }
            );
        } else {
            this.connectionStatusSubscription = this.batchService.getConnectionStatus().subscribe(
                status => {
                    this.connectionStatus = status;
                    console.log('SSE連線狀態變更:', status);
                }
            );
        }
    }

    /**
     * 開始串流日誌
     */
    startStreaming(): void {
        if (this.isStreaming) {
            return;
        }

        console.log('開始串流日誌，模式:', this.queryMode, '查詢條件:', this.currentCriteria);
        this.isStreaming = true;
        
        if (this.queryMode === 'optimized') {
            // 使用新的優化服務
            console.log('使用優化 SSE 服務');
            this.sseSubscription = this.optimizedLogService.streamLogs(this.currentCriteria).subscribe({
                next: (logs) => {
                    console.log('收到SSE日誌數據:', logs.length, '筆');
                    this.mergeNewLogs(logs);
                    this.applyFilter();
                },
                error: (error) => {
                    console.error('SSE串流錯誤:', error);
                    this.error = `串流連線錯誤: ${error.message}`;
                    this.isStreaming = false;
                }
            });
        } else {
            // 使用舊的服務
            console.log('使用傳統 SSE 服務');
            this.sseSubscription = this.batchService.connectToLogStream(this.currentFilter).subscribe({
                next: (logs) => {
                    console.log('收到SSE日誌數據:', logs.length, '筆');
                    this.mergeNewLogs(logs);
                    this.applyFilter();
                },
                error: (error) => {
                    console.error('SSE串流錯誤:', error);
                    this.error = `串流連線錯誤: ${error.message}`;
                    this.isStreaming = false;
                }
            });
        }
    }

    /**
     * 停止串流日誌
     */
    stopStreaming(): void {
        this.disconnectStream();
    }

    /**
     * 斷開SSE連線
     */
    private disconnectStream(): void {
        if (this.sseSubscription) {
            this.sseSubscription.unsubscribe();
            this.sseSubscription = undefined;
        }
        
        if (this.queryMode === 'optimized') {
            this.optimizedLogService.disconnectLogStream();
        } else {
            this.batchService.disconnectLogStream();
        }
        
        this.isStreaming = false;
        console.log('已停止日誌串流');
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
        if (this.queryMode === 'optimized') {
            this.currentCriteria = this.optimizedLogService.createQuickCriteria('recent');
            this.loadLogsWithCriteria();
        } else {
            // 預設載入最近24小時的日誌（舊方式）
            const now = new Date();
            const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);

            const startTime = yesterday.toISOString().slice(0, 19);
            const endTime = now.toISOString().slice(0, 19);

            this.batchService.getLogsByTimeRange(startTime, endTime).subscribe({
                next: (logs) => {
                    this.logs = logs;
                    this.applyFilter();
                    this.isLoading = false;
                },
                error: (error) => {
                    this.error = `載入日誌失敗: ${error.error || error.message}`;
                    this.isLoading = false;
                }
            });
        }
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
     * 處理新的查詢條件變化
     */
    onCriteriaChange(criteria: LogQueryCriteria): void {
        this.currentCriteria = { ...criteria };
        this.loadFilteredLogs();
        
        // 如果有執行ID，載入統計資訊
        if (criteria.executionId) {
            this.loadLogStats(criteria.executionId);
        } else {
            this.logStats = null;
        }
    }

    /**
     * 處理舊的過濾條件變化（向後兼容）
     */
    onFilterChange(filter: LogFilter): void {
        this.currentFilter = filter;
        
        if (this.queryMode === 'optimized') {
            // 轉換為新的查詢條件
            this.currentCriteria = this.optimizedLogService.convertFilterToCriteria({
                ...filter,
                logLevels: filter.logLevel ? [filter.logLevel] : undefined
            });
            this.loadFilteredLogs();
        } else {
            this.loadFilteredLogsLegacy();
        }
    }

    /**
     * 載入過濾後的日誌
     */
    loadFilteredLogs(): void {
        this.isLoading = true;
        this.error = '';

        if (this.queryMode === 'optimized') {
            this.loadLogsWithCriteria();
        } else {
            this.loadFilteredLogsLegacy();
        }
    }

    /**
     * 使用舊方式載入過濾後的日誌
     */
    private loadFilteredLogsLegacy(): void {
        if (this.currentFilter.startTime && this.currentFilter.endTime) {
            // 使用時間範圍查詢
            this.batchService.getLogsByTimeRange(
                this.currentFilter.startTime,
                this.currentFilter.endTime
            ).subscribe({
                next: (logs) => {
                    this.logs = logs;
                    this.applyFilter();
                    this.isLoading = false;
                },
                error: (error) => {
                    this.error = `載入日誌失敗: ${error.error || error.message}`;
                    this.isLoading = false;
                }
            });
        } else if (this.currentFilter.executionId) {
            // 使用執行代號查詢
            this.batchService.getLogsByExecutionId(this.currentFilter.executionId).subscribe({
                next: (logs) => {
                    this.logs = logs;
                    this.applyFilter();
                    this.isLoading = false;
                },
                error: (error) => {
                    this.error = `載入日誌失敗: ${error.error || error.message}`;
                    this.isLoading = false;
                }
            });
        } else if (this.currentFilter.jobName) {
            // 使用作業名稱查詢
            this.batchService.getLogsByJobName(this.currentFilter.jobName).subscribe({
                next: (logs) => {
                    this.logs = logs;
                    this.applyFilter();
                    this.isLoading = false;
                },
                error: (error) => {
                    this.error = `載入日誌失敗: ${error.error || error.message}`;
                    this.isLoading = false;
                }
            });
        } else {
            // 載入預設日誌
            this.loadInitialLogs();
        }
    }

    /**
     * 載入日誌統計資訊
     */
    private loadLogStats(executionId: string): void {
        if (this.queryMode === 'optimized') {
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
    }

    /**
     * 切換查詢模式
     */
    toggleQueryMode(): void {
        this.queryMode = this.queryMode === 'optimized' ? 'legacy' : 'optimized';
        
        // 重新訂閱連線狀態
        if (this.connectionStatusSubscription) {
            this.connectionStatusSubscription.unsubscribe();
        }
        this.subscribeToConnectionStatus();
        
        // 重新載入日誌
        this.loadInitialLogs();
        
        console.log('切換到查詢模式:', this.queryMode);
    }

    /**
     * 快速操作：顯示錯誤日誌
     */
    showErrorsOnly(): void {
        if (this.queryMode === 'optimized') {
            this.currentCriteria = this.optimizedLogService.createQuickCriteria('errors', this.currentCriteria.jobName);
            this.loadFilteredLogs();
        }
    }

    /**
     * 快速操作：顯示警告和錯誤
     */
    showWarningsAndErrors(): void {
        if (this.queryMode === 'optimized') {
            this.currentCriteria = this.optimizedLogService.createQuickCriteria('warnings', this.currentCriteria.jobName);
            this.loadFilteredLogs();
        }
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

        // 根據查詢模式應用不同的過濾邏輯
        if (this.queryMode === 'optimized') {
            // 新模式：大部分過濾已在後端完成，這裡只做額外的客戶端過濾
            // 關鍵字搜索（如果後端沒有完全處理）
            if (this.currentCriteria.keyword && this.currentCriteria.keyword.trim()) {
                const keyword = this.currentCriteria.keyword.toLowerCase();
                filtered = filtered.filter(log =>
                    log.message.toLowerCase().includes(keyword) ||
                    log.loggerName.toLowerCase().includes(keyword)
                );
            }
        } else {
            // 舊模式：客戶端過濾
            if (this.currentFilter.logLevel && this.currentFilter.logLevel !== 'ALL') {
                filtered = filtered.filter(log => log.logLevel === this.currentFilter.logLevel);
            }

            if (this.currentFilter.keyword && this.currentFilter.keyword.trim()) {
                const keyword = this.currentFilter.keyword.toLowerCase();
                filtered = filtered.filter(log =>
                    log.message.toLowerCase().includes(keyword) ||
                    log.loggerName.toLowerCase().includes(keyword)
                );
            }
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
} 