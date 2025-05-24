import { Component, OnInit, OnDestroy } from '@angular/core';
import { BatchService } from '../../services/batch.service';
import { BatchLog, LogFilter } from '../../models/batch-log.model';
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

    // 為模板提供 Math 對象訪問
    Math = Math;

    constructor(private batchService: BatchService) { }

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
        this.connectionStatusSubscription = this.batchService.getConnectionStatus().subscribe(
            status => {
                this.connectionStatus = status;
                console.log('SSE連線狀態變更:', status);
            }
        );
    }

    /**
     * 開始串流日誌
     */
    startStreaming(): void {
        if (this.isStreaming) {
            return;
        }

        this.isStreaming = true;
        this.sseSubscription = this.batchService.connectToLogStream(this.currentFilter).subscribe({
            next: (logs) => {
                console.log('收到SSE日誌數據:', logs.length, '筆');
                // 合併新日誌與現有日誌
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
        
        this.batchService.disconnectLogStream();
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
            this.logs = [...this.logs, ...uniqueNewLogs];
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

    loadInitialLogs(): void {
        this.isLoading = true;
        this.error = '';

        // 預設載入最近24小時的日誌
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

    onFilterChange(filter: LogFilter): void {
        this.currentFilter = filter;
        this.loadFilteredLogs();
    }

    loadFilteredLogs(): void {
        this.isLoading = true;
        this.error = '';

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

    applyFilter(): void {
        let filtered = [...this.logs];

        // 應用日誌級別過濾
        if (this.currentFilter.logLevel) {
            filtered = filtered.filter(log => log.logLevel === this.currentFilter.logLevel);
        }

        // 應用關鍵字過濾
        if (this.currentFilter.keyword) {
            const keyword = this.currentFilter.keyword.toLowerCase();
            filtered = filtered.filter(log =>
                log.message.toLowerCase().includes(keyword) ||
                log.loggerName.toLowerCase().includes(keyword) ||
                (log.exceptionStack && log.exceptionStack.toLowerCase().includes(keyword))
            );
        }

        // 排序
        filtered.sort((a, b) => {
            const aValue = this.getFieldValue(a, this.sortField);
            const bValue = this.getFieldValue(b, this.sortField);

            if (this.sortDirection === 'asc') {
                return aValue > bValue ? 1 : -1;
            } else {
                return aValue < bValue ? 1 : -1;
            }
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
            case 'message':
                return log.message;
            default:
                return log.logTime;
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
        const maxPages = Math.min(5, this.totalPages);
        const pages: number[] = [];
        for (let i = 1; i <= maxPages; i++) {
            pages.push(i);
        }
        return pages;
    }

    getLogLevelClass(level: string): string {
        switch (level?.toUpperCase()) {
            case 'ERROR':
                return 'log-level-error';
            case 'WARN':
                return 'log-level-warn';
            case 'INFO':
                return 'log-level-info';
            case 'DEBUG':
                return 'log-level-debug';
            default:
                return 'log-level-default';
        }
    }

    formatDateTime(dateTime: string): string {
        return new Date(dateTime).toLocaleString('zh-TW');
    }

    clearError(): void {
        this.error = '';
    }
} 