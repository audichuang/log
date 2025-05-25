import { Component, Inject, OnInit, OnDestroy, ViewChild, AfterViewInit, ChangeDetectorRef } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { BatchService } from '../../services/batch.service';
import { OptimizedLogService } from '../../services/optimized-log.service';
import { BatchLog, BatchJob, LogQueryCriteria, LOG_LEVELS } from '../../models/batch-log.model';
import { Subject, combineLatest } from 'rxjs';
import { takeUntil, debounceTime } from 'rxjs/operators';

export interface LogDialogData {
  job: BatchJob;
}

@Component({
  selector: 'app-log-dialog',
  templateUrl: './log-dialog.component.html',
  styleUrls: ['./log-dialog.component.scss']
})
export class LogDialogComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  displayedColumns: string[] = ['logTime', 'logLevel', 'executionId', 'message', 'threadName'];
  dataSource = new MatTableDataSource<BatchLog>();

  isLoading = false;
  error = '';

  readonly logLevels = LOG_LEVELS;
  selectedLogLevels: string[] = ['INFO', 'WARN', 'ERROR'];
  searchKeyword = '';
  availableExecutions: { executionId: string; executionTime: string; status: string }[] = [];
  selectedExecutionId = 'ALL';
  isLoadingExecutions = false;

  connectionStatus = 'disconnected';
  pollingStatus: any = {};

  // 🔥 新增：分頁控制
  isRealTimeMode = true; // 是否為實時模式
  currentPageIndex = 0;
  currentPageSize = 25;

  private readonly destroy$ = new Subject<void>();
  private filterUpdateTimeout: any;

  constructor(
      public dialogRef: MatDialogRef<LogDialogComponent>,
      @Inject(MAT_DIALOG_DATA) public data: LogDialogData,
      private batchService: BatchService,
      private optimizedLogService: OptimizedLogService,
      private cdr: ChangeDetectorRef // 🔥 新增
  ) {
    console.log('📱 LogDialogComponent 初始化');
  }

  // 🔥 新增：公開方法供模板使用
  isPollingConnected(): boolean {
    return this.optimizedLogService.isConnected();
  }

  ngOnInit(): void {
    this.loadExecutions();
    this.subscribeToServices();
    this.startPolling();
  }

  ngAfterViewInit(): void {
    // 🔥 修正：確保分頁器正確初始化
    this.setupPaginator();
    this.setupSort();
  }

  ngOnDestroy(): void {
    console.log('🗑️ LogDialogComponent 銷毀');
    this.destroy$.next();
    this.destroy$.complete();
    this.optimizedLogService.stopPollingLogs();

    if (this.filterUpdateTimeout) {
      clearTimeout(this.filterUpdateTimeout);
    }
  }

  /**
   * 🔥 設置分頁器
   */
  private setupPaginator(): void {
    if (this.paginator) {
      this.dataSource.paginator = this.paginator;

      // 監聽分頁變化
      this.paginator.page.pipe(takeUntil(this.destroy$)).subscribe(() => {
        this.currentPageIndex = this.paginator.pageIndex;
        this.currentPageSize = this.paginator.pageSize;
        console.log('📄 分頁變化:', this.currentPageIndex, this.currentPageSize);

        // 如果是實時模式且不在第一頁，切換為歷史模式
        if (this.isRealTimeMode && this.currentPageIndex > 0) {
          this.switchToHistoryMode();
        }
      });

      console.log('📄 分頁器已設置');
    }
  }

  /**
   * 🔥 設置排序
   */
  private setupSort(): void {
    if (this.sort) {
      this.dataSource.sort = this.sort;

      // 監聽排序變化
      this.sort.sortChange.pipe(takeUntil(this.destroy$)).subscribe(() => {
        console.log('🔤 排序變化:', this.sort.active, this.sort.direction);
        // 排序變化時重置分頁
        if (this.paginator) {
          this.paginator.firstPage();
        }
      });

      console.log('🔤 排序器已設置');
    }
  }

  /**
   * 訂閱服務狀態
   */
  private subscribeToServices(): void {
    // 訂閱日誌更新
    this.optimizedLogService.startPollingLogs(this.buildCurrentCriteria())
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (logs) => {
            console.log('📊 收到日誌更新:', logs.length, '條');
            this.updateDataSource(logs);
            this.isLoading = false;
            this.error = '';
          },
          error: (error) => {
            console.error('❌ 日誌更新錯誤:', error);
            this.error = `查詢失敗: ${error.message || error}`;
            this.isLoading = false;
          }
        });

    // 訂閱連線狀態
    this.optimizedLogService.getConnectionStatus()
        .pipe(takeUntil(this.destroy$))
        .subscribe(status => {
          this.connectionStatus = status;
          console.log('📡 連線狀態:', status);
        });

    // 定時更新輪詢狀態顯示
    setInterval(() => {
      if (!this.destroy$.closed) {
        this.pollingStatus = this.optimizedLogService.getPollingStatus();
      }
    }, 1000);
  }

  /**
   * 🔥 更新數據源（保持分頁狀態）
   */
  private updateDataSource(logs: BatchLog[]): void {
    const wasEmpty = this.dataSource.data.length === 0;

    // 🔥 如果是實時模式且在第一頁，直接更新數據
    if (this.isRealTimeMode && this.currentPageIndex === 0) {
      this.dataSource.data = logs;
      console.log('🔄 實時模式更新數據:', logs.length, '條');
    } else {
      // 🔥 如果不在第一頁或非實時模式，謹慎更新
      const existingData = this.dataSource.data;

      // 檢查是否有新數據（簡單檢查前幾筆）
      const hasNewData = logs.length > 0 && existingData.length > 0 &&
          logs[0]?.id !== existingData[0]?.id;

      if (hasNewData || wasEmpty) {
        this.dataSource.data = logs;
        console.log('🔄 非實時模式更新數據:', logs.length, '條');

        // 🔥 如果用戶不在第一頁，顯示提示
        if (this.currentPageIndex > 0) {
          this.showNewDataNotification();
        }
      }
    }

    // 🔥 重要：確保分頁器狀態正確
    this.maintainPaginatorState();
  }

  /**
   * 🔥 維持分頁器狀態
   */
  private maintainPaginatorState(): void {
    if (this.paginator && this.currentPageIndex > 0) {
      // 延遲執行，確保數據已更新
      setTimeout(() => {
        if (this.paginator) {
          // 檢查當前頁是否仍然有效
          const totalPages = Math.ceil(this.dataSource.data.length / this.currentPageSize);
          if (this.currentPageIndex >= totalPages) {
            // 如果當前頁超出範圍，跳到最後一頁
            this.paginator.lastPage();
          } else {
            // 恢復到之前的頁碼
            this.paginator.pageIndex = this.currentPageIndex;
          }
          this.cdr.detectChanges();
        }
      }, 0);
    }
  }

  /**
   * 🔥 切換到歷史模式
   */
  private switchToHistoryMode(): void {
    if (this.isRealTimeMode) {
      this.isRealTimeMode = false;
      console.log('📚 切換到歷史模式');

      // 停止實時輪詢
      this.optimizedLogService.stopPollingLogs();

      // 顯示通知
      this.showModeChangeNotification('已切換到歷史模式，不會自動更新');
    }
  }

  /**
   * 🔥 切換回實時模式
   */
  switchToRealTimeMode(): void {
    if (!this.isRealTimeMode) {
      this.isRealTimeMode = true;
      console.log('🔄 切換到實時模式');

      // 重新開始輪詢
      this.startPolling();

      // 跳回第一頁
      if (this.paginator) {
        this.paginator.firstPage();
      }

      this.showModeChangeNotification('已切換到實時模式，將自動更新');
    }
  }

  /**
   * 🔥 顯示新數據通知
   */
  private showNewDataNotification(): void {
    // 這裡可以顯示一個 snackbar 或其他通知
    console.log('💡 有新數據，但您不在第一頁');
  }

  /**
   * 🔥 顯示模式變更通知
   */
  private showModeChangeNotification(message: string): void {
    console.log('💡', message);
    // 這裡可以顯示 snackbar
  }

  /**
   * 開始輪詢
   */
  private startPolling(): void {
    const criteria = this.buildCurrentCriteria();
    console.log('🚀 開始輪詢，條件:', criteria);
    this.isLoading = true;

    this.optimizedLogService.updatePollingFilter(criteria);
  }

  /**
   * 構建當前的查詢條件
   */
  private buildCurrentCriteria(): Partial<LogQueryCriteria> {
    const criteria: Partial<LogQueryCriteria> = {};

    if (this.selectedExecutionId !== 'ALL') {
      criteria.executionId = this.selectedExecutionId;
    } else {
      criteria.jobName = this.data.job.name;
    }

    if (this.selectedLogLevels.length > 0) {
      criteria.logLevels = this.selectedLogLevels;
    }

    if (this.searchKeyword.trim()) {
      criteria.keyword = this.searchKeyword.trim();
    }

    console.log('🔧 構建查詢條件:', criteria);
    return criteria;
  }

  /**
   * 載入執行記錄
   */
  loadExecutions(): void {
    if (!this.data.job.name) return;

    console.log('📋 載入執行記錄:', this.data.job.name);
    this.isLoadingExecutions = true;

    combineLatest([
      this.batchService.getJobExecutions(this.data.job.name),
      this.batchService.getJobExecutionIds(this.data.job.name)
    ]).pipe(takeUntil(this.destroy$))
        .subscribe({
          next: ([executions, executionIds]) => {
            const executionMap = new Map(executions.map(exec => [exec.executionId, exec]));

            this.availableExecutions = executionIds.map(executionId => ({
              executionId: executionId,
              executionTime: executionMap.get(executionId)?.startTime || '-',
              status: executionMap.get(executionId)?.status || 'UNKNOWN'
            }));

            console.log('📋 載入執行記錄完成:', this.availableExecutions.length, '個');
            this.isLoadingExecutions = false;
          },
          error: (error) => {
            console.error('❌ 載入執行記錄失敗:', error);
            this.error = '載入執行記錄失敗';
            this.isLoadingExecutions = false;
          }
        });
  }

  /**
   * 🔥 統一的過濾條件更新處理（防抖）
   */
  private updateFilterWithDebounce(): void {
    if (this.filterUpdateTimeout) {
      clearTimeout(this.filterUpdateTimeout);
    }

    this.isLoading = true;

    this.filterUpdateTimeout = setTimeout(() => {
      console.log('🔄 應用過濾條件變更');

      // 🔥 過濾條件變更時，重置為實時模式並回到第一頁
      this.isRealTimeMode = true;
      if (this.paginator) {
        this.paginator.firstPage();
      }

      const criteria = this.buildCurrentCriteria();
      this.optimizedLogService.updatePollingFilter(criteria);
    }, 300);
  }

  // 🔥 其他方法保持不變，但添加分頁重置
  onLogLevelsSelectionChange(selectedLevels: string[]): void {
    this.selectedLogLevels = selectedLevels;
    console.log('🏷️ 日誌級別變更:', this.selectedLogLevels);
    this.updateFilterWithDebounce();
  }

  onExecutionIdChange(): void {
    console.log('🆔 執行ID變更:', this.selectedExecutionId);
    this.updateFilterWithDebounce();
  }

  onKeywordChange(): void {
    console.log('🔍 關鍵字變更:', this.searchKeyword);
    this.updateFilterWithDebounce();
  }

  removeLogLevel(level: string): void {
    this.selectedLogLevels = this.selectedLogLevels.filter(l => l !== level);
    console.log('➖ 移除日誌級別:', level);
    this.updateFilterWithDebounce();
  }

  toggleAllLogLevels(): void {
    if (this.selectedLogLevels.length === this.logLevels.length) {
      this.selectedLogLevels = [];
    } else {
      this.selectedLogLevels = this.logLevels.map(level => level.value);
    }
    console.log('🔄 切換全選:', this.selectedLogLevels);
    this.updateFilterWithDebounce();
  }

  refreshLogs(): void {
    console.log('🔄 手動刷新日誌');
    this.isRealTimeMode = true;
    if (this.paginator) {
      this.paginator.firstPage();
    }
    this.optimizedLogService.refresh();
  }

  togglePolling(): void {
    if (this.isPollingConnected()) {
      console.log('⏸️ 暫停輪詢');
      this.isRealTimeMode = false;
      this.optimizedLogService.stopPollingLogs();
    } else {
      console.log('▶️ 恢復輪詢');
      this.switchToRealTimeMode();
    }
  }

  clearLogs(): void {
    console.log('🧹 清空日誌顯示');
    this.optimizedLogService.clearLogs();
    this.dataSource.data = [];
    if (this.paginator) {
      this.paginator.firstPage();
    }
  }

  // ====== 輔助方法 ======
  getLogLevelColor(level: string): string {
    const logLevel = this.logLevels.find(l => l.value === level);
    return logLevel?.color || '#6c757d';
  }

  getLogLevelClass(level: string): string {
    switch (level?.toUpperCase()) {
      case 'ERROR':
        return 'error-level';
      case 'WARN':
        return 'warn-level';
      case 'INFO':
        return 'info-level';
      case 'DEBUG':
        return 'debug-level';
      default:
        return 'default-level';
    }
  }

  formatDateTime(dateTime: string): string {
    return new Date(dateTime).toLocaleString('zh-TW');
  }

  closeDialog(): void {
    this.dialogRef.close();
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'RUNNING': return 'play_circle';
      case 'COMPLETED': return 'check_circle';
      case 'FAILED': return 'error';
      case 'IDLE': return 'pause_circle';
      default: return 'help';
    }
  }

  getStatusText(status: string): string {
    switch (status) {
      case 'RUNNING': return '執行中';
      case 'COMPLETED': return '已完成';
      case 'FAILED': return '執行失敗';
      case 'IDLE': return '待命中';
      default: return '未知';
    }
  }

  showExceptionStack(exceptionStack: string): void {
    console.log('Exception Stack:', exceptionStack);
  }

  getConnectionStatusText(): string {
    switch (this.connectionStatus) {
      case 'connected': return '已連線';
      case 'connecting': return '連線中...';
      case 'disconnected': return '已斷線';
      case 'error': return '連線錯誤';
      default: return '未知狀態';
    }
  }

  getConnectionStatusIcon(): string {
    switch (this.connectionStatus) {
      case 'connected': return 'wifi';
      case 'connecting': return 'wifi_off';
      case 'disconnected': return 'wifi_off';
      case 'error': return 'error';
      default: return 'help';
    }
  }
}