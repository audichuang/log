import { Component, Inject, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { BatchService } from '../../services/batch.service';
import { OptimizedLogService } from '../../services/optimized-log.service';
import { BatchLog, BatchJob, LogQueryCriteria, LOG_LEVELS } from '../../models/batch-log.model';
import { Subscription } from 'rxjs';

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

  displayedColumns: string[] = ['logTime', 'logLevel', 'message', 'threadName'];
  dataSource = new MatTableDataSource<BatchLog>();

  isLoading = false;
  error = '';
  
  // 新的多選日誌級別功能
  readonly logLevels = LOG_LEVELS;
  selectedLogLevels: string[] = [];
  
  // 保留舊的單選功能以向後兼容
  legacyLogLevels = ['ALL', 'DEBUG', 'INFO', 'WARN', 'ERROR'];
  selectedLogLevel = 'ALL';
  
  searchKeyword = '';
  
  availableExecutions: { executionId: string; executionTime: string; status: string }[] = [];
  selectedExecutionId = 'ALL';
  isLoadingExecutions = false;
  
  // 查詢模式切換
  useOptimizedMode = true;
  
  private logSubscription?: Subscription;
  connectionStatus = 'disconnected';

  constructor(
    public dialogRef: MatDialogRef<LogDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: LogDialogData,
    private batchService: BatchService,
    private optimizedLogService: OptimizedLogService
  ) {
    // 預設選擇所有日誌級別
    this.selectedLogLevels = this.logLevels.map(level => level.value);
    console.log('LogDialogComponent 初始化，使用優化模式:', this.useOptimizedMode);
  }

  ngOnInit(): void {
    this.loadJobExecutions();
  }

  ngOnDestroy(): void {
    this.batchService.disconnectLogStream();
    if (this.logSubscription) {
      this.logSubscription.unsubscribe();
    }
  }

  ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;

    // 自定義過濾邏輯
    this.dataSource.filterPredicate = (data: BatchLog, filter: string): boolean => {
      if (!filter) return true;

      try {
        const searchFilter = JSON.parse(filter);

        // 日誌級別過濾
        if (searchFilter.logLevel && searchFilter.logLevel !== 'ALL' && data.logLevel !== searchFilter.logLevel) {
          return false;
        }

        // 關鍵字過濾
        if (searchFilter.keyword) {
          const keyword = searchFilter.keyword.toLowerCase();
          const messageMatch = data.message.toLowerCase().includes(keyword);
          const loggerMatch = data.loggerName.toLowerCase().includes(keyword);
          const stackMatch = data.exceptionStack ? data.exceptionStack.toLowerCase().includes(keyword) : false;
          return messageMatch || loggerMatch || stackMatch;
        }

        return true;
      } catch {
        return true;
      }
    };
  }

  loadLogs(): void {
    this.isLoading = true;
    this.error = '';

    // 使用 SSE 方式載入日誌，有過濾條件時會立即返回結果
    this.setupSSEConnection();
  }

  setupSSEConnection(): void {
    console.log('設置 SSE 連接，使用優化模式:', this.useOptimizedMode);
    
    if (this.useOptimizedMode) {
      // 使用新的優化服務
      const criteria: Partial<LogQueryCriteria> = {};
      
      // 如果選擇了特定的執行ID，優先使用執行ID查詢
      if (this.selectedExecutionId !== 'ALL') {
        criteria.executionId = this.selectedExecutionId;
      } else {
        // 否則使用作業名稱查詢
        criteria.jobName = this.data.job.name;
      }
      
      // 添加選中的日誌級別
      if (this.selectedLogLevels.length > 0 && this.selectedLogLevels.length < this.logLevels.length) {
        criteria.logLevels = this.selectedLogLevels;
      }

      console.log('建立優化 SSE 連接，查詢條件:', criteria);

      // 建立 SSE 連接
      this.logSubscription = this.optimizedLogService.streamLogs(criteria).subscribe({
        next: (logs) => {
          console.log('收到日誌數據:', logs.length, '條');
          this.dataSource.data = logs;
          this.isLoading = false;
          this.error = '';
        },
        error: (error) => {
          console.error('優化 SSE 連接錯誤:', error);
          this.error = `載入日誌失敗: ${error.error || error.message}`;
          this.isLoading = false;
        }
      });

      // 監控連接狀態
      this.optimizedLogService.getConnectionStatus().subscribe({
        next: (status) => {
          this.connectionStatus = status;
          console.log('優化 SSE 連接狀態:', status);
        }
      });
    } else {
      // 使用舊的服務（向後兼容）
      const filter: any = {};
      
      if (this.selectedExecutionId !== 'ALL') {
        filter.executionId = this.selectedExecutionId;
      } else {
        filter.jobName = this.data.job.name;
      }

      console.log('建立傳統 SSE 連接，過濾條件:', filter);

      this.logSubscription = this.batchService.connectToLogStream(filter).subscribe({
        next: (logs) => {
          console.log('收到日誌數據:', logs.length, '條');
          this.dataSource.data = logs;
          this.isLoading = false;
          this.error = '';
        },
        error: (error) => {
          console.error('傳統 SSE 連接錯誤:', error);
          this.error = `載入日誌失敗: ${error.error || error.message}`;
          this.isLoading = false;
        }
      });

      this.batchService.getConnectionStatus().subscribe({
        next: (status) => {
          this.connectionStatus = status;
          console.log('傳統 SSE 連接狀態:', status);
        }
      });
    }
  }

  applyFilter(): void {
    const filterValue = JSON.stringify({
      logLevel: this.selectedLogLevel,
      keyword: this.searchKeyword
    });

    this.dataSource.filter = filterValue;

    if (this.dataSource.paginator) {
      this.dataSource.paginator.firstPage();
    }
  }

  onLogLevelChange(): void {
    this.applyFilter();
  }

  onKeywordChange(): void {
    this.applyFilter();
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

  refreshLogs(): void {
    // 重新建立 SSE 連接
    if (this.useOptimizedMode) {
      this.optimizedLogService.disconnectLogStream();
    } else {
      this.batchService.disconnectLogStream();
    }
    this.setupSSEConnection();
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
    // 這裡可以開啟另一個彈窗顯示例外堆疊
    console.log('Exception Stack:', exceptionStack);
  }

  /**
   * 載入作業的執行歷史記錄
   */
  loadJobExecutions(): void {
    this.isLoadingExecutions = true;
    
    this.batchService.getJobExecutions(this.data.job.name).subscribe({
      next: (executions) => {
        this.availableExecutions = executions.map(exec => ({
          executionId: exec.executionId,
          executionTime: exec.startTime,
          status: exec.status
        }));
        this.isLoadingExecutions = false;
        
        // 自動載入日誌
        this.loadLogs();
      },
      error: (error) => {
        console.error('載入執行歷史失敗:', error);
        this.isLoadingExecutions = false;
        // 即使載入執行歷史失敗，仍然嘗試載入日誌
        this.loadLogs();
      }
    });
  }

  /**
   * 當執行ID選擇改變時
   */
  onExecutionIdChange(): void {
    console.log('執行ID選擇改變:', this.selectedExecutionId);
    this.refreshLogs();
  }

  /**
   * 多選日誌級別相關方法
   */
  isLogLevelSelected(level: string): boolean {
    return this.selectedLogLevels.includes(level);
  }

  onLogLevelToggle(level: string, checked: boolean): void {
    if (checked) {
      if (!this.selectedLogLevels.includes(level)) {
        this.selectedLogLevels.push(level);
      }
    } else {
      this.selectedLogLevels = this.selectedLogLevels.filter(l => l !== level);
    }
    console.log('已選擇的日誌級別:', this.selectedLogLevels);
    this.applyFilter();
  }

  toggleAllLogLevels(): void {
    if (this.selectedLogLevels.length === this.logLevels.length) {
      // 當前全選，改為全不選
      this.selectedLogLevels = [];
    } else {
      // 當前非全選，改為全選
      this.selectedLogLevels = this.logLevels.map(level => level.value);
    }
    console.log('切換全選狀態，當前選擇:', this.selectedLogLevels);
    this.applyFilter();
  }

  getLogLevelColor(level: string): string {
    const logLevel = this.logLevels.find(l => l.value === level);
    return logLevel?.color || '#6c757d';
  }

  toggleOptimizedMode(): void {
    console.log('切換查詢模式，從', this.useOptimizedMode ? '優化' : '傳統', '到', !this.useOptimizedMode ? '優化' : '傳統');
    this.useOptimizedMode = !this.useOptimizedMode;
    
    // 斷開當前連接
    if (this.useOptimizedMode) {
      this.batchService.disconnectLogStream();
    } else {
      this.optimizedLogService.disconnectLogStream();
    }
    
    // 重新建立連接
    this.setupSSEConnection();
  }
}
