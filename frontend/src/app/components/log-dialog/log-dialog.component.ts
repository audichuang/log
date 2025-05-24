import { Component, Inject, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { BatchService } from '../../services/batch.service';
import { BatchLog, BatchJob } from '../../models/batch-log.model';

export interface LogDialogData {
  job: BatchJob;
  executionId?: string;
}

@Component({
  selector: 'app-log-dialog',
  templateUrl: './log-dialog.component.html',
  styleUrls: ['./log-dialog.component.scss']
})
export class LogDialogComponent implements OnInit {
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  displayedColumns: string[] = ['logTime', 'logLevel', 'message', 'threadName'];
  dataSource = new MatTableDataSource<BatchLog>();

  isLoading = false;
  error = '';
  logLevels = ['ALL', 'DEBUG', 'INFO', 'WARN', 'ERROR'];
  selectedLogLevel = 'ALL';
  searchKeyword = '';

  constructor(
    public dialogRef: MatDialogRef<LogDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: LogDialogData,
    private batchService: BatchService
  ) { }

  ngOnInit(): void {
    this.loadLogs();
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

    let logObservable;

    if (this.data.executionId) {
      logObservable = this.batchService.getLogsByExecutionId(this.data.executionId);
    } else {
      logObservable = this.batchService.getLogsByJobName(this.data.job.name);
    }

    logObservable.subscribe({
      next: (logs) => {
        this.dataSource.data = logs;
        this.isLoading = false;
      },
      error: (error) => {
        this.error = `載入日誌失敗: ${error.error || error.message}`;
        this.isLoading = false;
      }
    });
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
    this.loadLogs();
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
}
