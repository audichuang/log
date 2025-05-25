import { Component, Inject, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { BatchService } from '../../services/batch.service';
import { OptimizedLogService } from '../../services/optimized-log.service';
import { BatchLog, BatchJob, LogQueryCriteria, LOG_LEVELS } from '../../models/batch-log.model';
import { Subscription, Subject, combineLatest } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, takeUntil, tap } from 'rxjs/operators';

export interface LogDialogData {
  job: BatchJob;
}

@Component({
  selector: 'app-log-dialog',
  templateUrl: './log-dialog.component.html',
  styleUrls: ['./log-dialog.component.scss']
})
export class LogDialogComponent implements OnInit, OnDestroy {
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  displayedColumns: string[] = ['logTime', 'logLevel', 'executionId', 'message', 'threadName'];
  dataSource = new MatTableDataSource<BatchLog>();

  isLoading = false;
  error = '';
  
  // 多選日誌級別功能
  readonly logLevels = LOG_LEVELS;
  selectedLogLevels: string[] = [];
  
  searchKeyword = '';
  
  availableExecutions: { executionId: string; executionTime: string; status: string }[] = [];
  selectedExecutionId = 'ALL';
  isLoadingExecutions = false;
  
  connectionStatus = 'disconnected';

  // RxJS Subjects for reactive updates
  private readonly logLevelChange$ = new Subject<string[]>();
  private readonly executionIdChange$ = new Subject<string>();
  private readonly keywordChange$ = new Subject<string>();
  private readonly destroy$ = new Subject<void>();

  constructor(
    public dialogRef: MatDialogRef<LogDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: LogDialogData,
    private batchService: BatchService,
    private optimizedLogService: OptimizedLogService
  ) {
    // 預設選擇所有日誌級別
    this.selectedLogLevels = this.logLevels.map(level => level.value);
    console.log('LogDialogComponent 初始化，使用優化模式');
  }

  ngOnInit(): void {
    this.setupReactiveFilterStream();
    this.loadExecutions();
    this.setupInitialConnection();
    this.subscribeToConnectionStatus();
  }

  ngOnDestroy(): void {
    // 發送銷毀信號，停止所有observable
    this.destroy$.next();
    this.destroy$.complete();
    
    // 確保斷開SSE連接
    this.optimizedLogService.disconnectLogStream();
    
    console.log('LogDialogComponent 已銷毀，所有連接已清理');
  }

  /**
   * 設置響應式過濾流，使用RxJS管理所有過濾條件變化
   */
  private setupReactiveFilterStream(): void {
    // 合併所有過濾條件變化，使用debounce避免頻繁請求
    combineLatest([
      this.logLevelChange$.pipe(
        distinctUntilChanged((prev, curr) => JSON.stringify(prev) === JSON.stringify(curr))
      ),
      this.executionIdChange$.pipe(distinctUntilChanged()),
      this.keywordChange$.pipe(distinctUntilChanged())
    ]).pipe(
      debounceTime(300), // 300ms延遲，避免過於頻繁的查詢
      tap(() => {
        console.log('過濾條件已變化，準備重新查詢');
        // 確保先斷開舊連接
        this.optimizedLogService.disconnectLogStream();
      }),
      switchMap(([logLevels, executionId, keyword]) => {
        // 建立新的查詢連接
        return this.createSSEConnection({
          logLevels,
          executionId: executionId === 'ALL' ? undefined : executionId,
          keyword: keyword || undefined
        });
      }),
      takeUntil(this.destroy$)
    ).subscribe({
      next: (logs) => {
        console.log('收到響應式查詢結果:', logs.length, '條日誌');
        this.dataSource.data = logs;
        this.isLoading = false;
        this.error = '';
      },
      error: (error) => {
        console.error('響應式查詢錯誤:', error);
        this.error = `查詢失敗: ${error.message || error}`;
        this.isLoading = false;
      }
    });
  }

  /**
   * 建立SSE連接的統一方法
   */
  private createSSEConnection(criteria: Partial<LogQueryCriteria>) {
    this.isLoading = true;
    
    // 根據選擇的執行ID或作業名稱設置查詢條件
    const finalCriteria: Partial<LogQueryCriteria> = {
      ...criteria,
      jobName: criteria.executionId ? undefined : this.data.job.name
    };

    console.log('建立SSE連接，查詢條件:', finalCriteria);
    return this.optimizedLogService.streamLogs(finalCriteria);
  }

  /**
   * 訂閱連線狀態
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
   * 設置初始連接
   */
  private setupInitialConnection(): void {
    // 觸發初始查詢
    this.logLevelChange$.next(this.selectedLogLevels);
    this.executionIdChange$.next(this.selectedExecutionId);
    this.keywordChange$.next(this.searchKeyword);
  }

  ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  loadExecutions(): void {
    if (!this.data.job.name) {
      console.log('loadExecutions: 沒有作業名稱');
      return;
    }
    
    console.log('loadExecutions: 開始載入執行記錄，作業名稱:', this.data.job.name);
    this.isLoadingExecutions = true;
    
    // 同時調用兩個API：執行歷史和執行代號
    const executions$ = this.batchService.getJobExecutions(this.data.job.name);
    const executionIds$ = this.batchService.getJobExecutionIds(this.data.job.name);
    
    console.log('loadExecutions: 準備調用API...');
    
    combineLatest([executions$, executionIds$])
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ([executions, executionIds]) => {
          console.log('loadExecutions: API回應成功');
          console.log('executions:', executions.length, '個');
          console.log('executionIds:', executionIds.length, '個');
          
          // 建立執行代號到執行記錄的映射
          const executionMap = new Map(executions.map(exec => [exec.executionId, exec]));
          
          // 組合資料，優先使用執行代號列表（因為可能有更多）
          this.availableExecutions = executionIds.map(executionId => {
            const execution = executionMap.get(executionId);
            return {
              executionId: executionId,
              executionTime: execution?.startTime || '-',
              status: execution?.status || 'UNKNOWN'
            };
          });
          
          console.log('loadExecutions: 最終可用執行記錄:', this.availableExecutions.length, '個');
          console.log('loadExecutions: availableExecutions:', this.availableExecutions.slice(0, 3));
          this.isLoadingExecutions = false;
        },
        error: (error) => {
          console.error('loadExecutions: API調用失敗:', error);
          this.error = '載入執行記錄失敗: ' + (error.message || error);
          this.isLoadingExecutions = false;
        }
      });
  }

  /**
   * 多選下拉式選單的選擇變化處理
   */
  onLogLevelsSelectionChange(selectedLevels: string[]): void {
    this.selectedLogLevels = selectedLevels;
    console.log('下拉式選單日誌級別已變化:', this.selectedLogLevels);
    this.logLevelChange$.next([...this.selectedLogLevels]);
  }

  /**
   * 移除單個日誌級別
   */
  removeLogLevel(level: string): void {
    this.selectedLogLevels = this.selectedLogLevels.filter(l => l !== level);
    console.log('移除日誌級別:', level, '剩餘:', this.selectedLogLevels);
    this.logLevelChange$.next([...this.selectedLogLevels]);
  }

  toggleAllLogLevels(): void {
    if (this.selectedLogLevels.length === this.logLevels.length) {
      this.selectedLogLevels = [];
    } else {
      this.selectedLogLevels = this.logLevels.map(level => level.value);
    }
    
    console.log('切換全選狀態:', this.selectedLogLevels);
    this.logLevelChange$.next([...this.selectedLogLevels]);
  }

  /**
   * 獲取日誌級別顏色
   */
  getLogLevelColor(level: string): string {
    const logLevel = this.logLevels.find(l => l.value === level);
    return logLevel?.color || '#6c757d';
  }

  /**
   * 響應式方法：執行ID變化
   */
  onExecutionIdChange(): void {
    console.log('執行ID已變化:', this.selectedExecutionId);
    this.executionIdChange$.next(this.selectedExecutionId);
  }

  /**
   * 響應式方法：關鍵字變化
   */
  onKeywordChange(): void {
    console.log('搜尋關鍵字已變化:', this.searchKeyword);
    this.keywordChange$.next(this.searchKeyword);
  }

  /**
   * 手動刷新
   */
  refreshLogs(): void {
    console.log('手動刷新日誌');
    this.optimizedLogService.disconnectLogStream();
    this.setupInitialConnection();
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
      case 'RUNNING':
        return 'play_circle';
      case 'COMPLETED':
        return 'check_circle';
      case 'FAILED':
        return 'error';
      case 'IDLE':
        return 'pause_circle';
      default:
        return 'help';
    }
  }

  getStatusText(status: string): string {
    switch (status) {
      case 'RUNNING':
        return '執行中';
      case 'COMPLETED':
        return '已完成';
      case 'FAILED':
        return '執行失敗';
      case 'IDLE':
        return '待命中';
      default:
        return '未知';
    }
  }

  showExceptionStack(exceptionStack: string): void {
    console.log('Exception Stack:', exceptionStack);
  }
}
