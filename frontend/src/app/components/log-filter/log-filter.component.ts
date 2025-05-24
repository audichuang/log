import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { 
    LogFilter, 
    LogQueryCriteria, 
    LogFilterOptions, 
    BatchJob, 
    BatchExecution, 
    LOG_LEVELS, 
    LogLevel 
} from '../../models/batch-log.model';
import { BatchService } from '../../services/batch.service';
import { OptimizedLogService } from '../../services/optimized-log.service';

@Component({
    selector: 'app-log-filter',
    templateUrl: './log-filter.component.html',
    styleUrls: ['./log-filter.component.scss']
})
export class LogFilterComponent implements OnInit {
    @Output() filterChange = new EventEmitter<LogQueryCriteria>();
    @Output() legacyFilterChange = new EventEmitter<LogFilter>(); // 向後兼容

    filterForm: FormGroup;
    
    // 日誌級別相關
    readonly logLevels = LOG_LEVELS;
    selectedLogLevels: string[] = [];

    // 作業和執行歷史相關
    availableJobs: BatchJob[] = [];
    jobExecutions: BatchExecution[] = [];
    selectedJobName: string = '';
    isLoadingExecutions = false;

    // 查詢類型選項
    queryTypes = [
        { value: 'HISTORICAL', label: '歷史查詢' },
        { value: 'REAL_TIME', label: '實時查詢' },
        { value: 'SEARCH', label: '搜尋查詢' }
    ];

    // 排序選項
    sortDirections = [
        { value: 'DESC', label: '最新優先' },
        { value: 'ASC', label: '最舊優先' }
    ];

    // 快速時間範圍選項
    quickTimeRanges = [
        { label: '最近1小時', hours: 1 },
        { label: '最近6小時', hours: 6 },
        { label: '最近24小時', hours: 24 },
        { label: '最近3天', hours: 72 },
        { label: '最近7天', hours: 168 }
    ];

    constructor(
        private fb: FormBuilder,
        private batchService: BatchService,
        private optimizedLogService: OptimizedLogService
    ) {
        this.filterForm = this.fb.group({
            startTime: [''],
            endTime: [''],
            keyword: [''],
            selectedJobName: [''],
            executionId: [''],
            stepName: [''],
            limit: [100],
            sortDirection: ['DESC'],
            queryType: ['HISTORICAL'],
            // 舊的欄位，保留向後相容
            logLevel: [''],
            jobName: ['']
        });
    }

    ngOnInit(): void {
        // 載入可用的作業清單
        this.loadAvailableJobs();

        // 設定預設時間範圍為過去24小時
        this.setQuickTimeRange(24);

        // 預設選擇所有日誌級別
        this.selectedLogLevels = this.logLevels.map(level => level.value);

        // 監聽作業選擇變化
        this.filterForm.get('selectedJobName')?.valueChanges.subscribe((jobName: string) => {
            this.onJobSelected(jobName);
        });

        // 監聽其他表單變化
        this.filterForm.valueChanges.subscribe(() => {
            this.applyFilter();
        });

        // 初始觸發過濾
        this.applyFilter();
    }

    /**
     * 載入可用的作業清單
     */
    private loadAvailableJobs(): void {
        this.batchService.getBatchJobs().subscribe({
            next: (jobs) => {
                this.availableJobs = jobs;
            },
            error: (error) => {
                console.error('載入作業清單失敗:', error);
            }
        });
    }

    /**
     * 當作業被選擇時載入其執行歷史
     */
    private onJobSelected(jobName: string): void {
        this.selectedJobName = jobName;
        this.jobExecutions = [];
        
        if (!jobName) {
            this.filterForm.patchValue({ executionId: '' });
            return;
        }

        this.isLoadingExecutions = true;
        this.batchService.getJobExecutions(jobName).subscribe({
            next: (executions) => {
                this.jobExecutions = executions.sort((a, b) => 
                    new Date(b.startTime).getTime() - new Date(a.startTime).getTime()
                );
                this.isLoadingExecutions = false;

                // 如果有執行歷史，預設選擇最新的
                if (this.jobExecutions.length > 0) {
                    this.filterForm.patchValue({ 
                        executionId: this.jobExecutions[0].executionId 
                    });
                }
            },
            error: (error) => {
                console.error('載入執行歷史失敗:', error);
                this.isLoadingExecutions = false;
            }
        });
    }

    /**
     * 處理日誌級別選擇變化
     */
    onLogLevelChange(level: string, checked: boolean): void {
        if (checked) {
            if (!this.selectedLogLevels.includes(level)) {
                this.selectedLogLevels.push(level);
            }
        } else {
            this.selectedLogLevels = this.selectedLogLevels.filter(l => l !== level);
        }
        this.applyFilter();
    }

    /**
     * 檢查日誌級別是否被選中
     */
    isLogLevelSelected(level: string): boolean {
        return this.selectedLogLevels.includes(level);
    }

    /**
     * 全選/反選日誌級別
     */
    toggleAllLogLevels(): void {
        if (this.selectedLogLevels.length === this.logLevels.length) {
            // 當前全選，改為全不選
            this.selectedLogLevels = [];
        } else {
            // 當前非全選，改為全選
            this.selectedLogLevels = this.logLevels.map(level => level.value);
        }
        this.applyFilter();
    }

    /**
     * 獲取日誌級別的顏色
     */
    getLogLevelColor(level: string): string {
        const logLevel = this.logLevels.find(l => l.value === level);
        return logLevel?.color || '#6c757d';
    }

    /**
     * 設定快速時間範圍
     */
    setQuickTimeRange(hours: number): void {
        const now = new Date();
        const past = new Date(now.getTime() - hours * 60 * 60 * 1000);

        this.filterForm.patchValue({
            startTime: this.formatDateTime(past),
            endTime: this.formatDateTime(now)
        });
    }

    /**
     * 格式化執行記錄的顯示文字
     */
    formatExecutionDisplay(execution: BatchExecution): string {
        const startTime = new Date(execution.startTime);
        const timeStr = startTime.toLocaleString('zh-TW');
        const statusStr = this.getStatusDisplayText(execution.status);
        const durationStr = execution.endTime ? 
            this.calculateDuration(execution.startTime, execution.endTime) : '執行中';
        
        return `${timeStr} (${statusStr}, ${durationStr})`;
    }

    /**
     * 取得狀態顯示文字
     */
    getStatusDisplayText(status: string): string {
        switch (status) {
            case 'RUNNING': return '執行中';
            case 'COMPLETED': return '已完成';
            case 'FAILED': return '失敗';
            case 'STOPPED': return '已停止';
            default: return status;
        }
    }

    /**
     * 計算執行時長
     */
    calculateDuration(startTime: string, endTime: string): string {
        const start = new Date(startTime);
        const end = new Date(endTime);
        const diffMs = end.getTime() - start.getTime();
        const diffSec = Math.floor(diffMs / 1000);
        
        if (diffSec < 60) {
            return `${diffSec}秒`;
        } else if (diffSec < 3600) {
            const minutes = Math.floor(diffSec / 60);
            const seconds = diffSec % 60;
            return `${minutes}分${seconds}秒`;
        } else {
            const hours = Math.floor(diffSec / 3600);
            const minutes = Math.floor((diffSec % 3600) / 60);
            return `${hours}小時${minutes}分鐘`;
        }
    }

    /**
     * 格式化執行時間顯示
     */
    formatExecutionTime(startTime: string): string {
        return new Date(startTime).toLocaleString('zh-TW');
    }

    private formatDateTime(date: Date): string {
        return date.toISOString().slice(0, 16);
    }

    /**
     * 應用過濾條件（新版本）
     */
    applyFilter(): void {
        const formValue = this.filterForm.value;
        
        // 新的查詢條件
        const criteria: LogQueryCriteria = {
            executionId: formValue.executionId || undefined,
            jobName: formValue.selectedJobName || formValue.jobName || undefined,
            stepName: formValue.stepName || undefined,
            logLevels: this.selectedLogLevels.length > 0 ? this.selectedLogLevels : undefined,
            keyword: formValue.keyword || undefined,
            startTime: formValue.startTime || undefined,
            endTime: formValue.endTime || undefined,
            limit: formValue.limit || 100,
            sortDirection: formValue.sortDirection || 'DESC',
            queryType: formValue.queryType || 'HISTORICAL'
        };

        this.filterChange.emit(criteria);

        // 向後兼容：發送舊格式的過濾條件
        const legacyFilter: LogFilter = {
            executionId: criteria.executionId,
            jobName: criteria.jobName,
            keyword: criteria.keyword,
            startTime: criteria.startTime,
            endTime: criteria.endTime,
            logLevel: this.selectedLogLevels.length === 1 ? this.selectedLogLevels[0] : undefined
        };

        this.legacyFilterChange.emit(legacyFilter);
    }

    /**
     * 重置過濾條件
     */
    resetFilter(): void {
        // 重置表單
        this.filterForm.reset({
            keyword: '',
            selectedJobName: '',
            executionId: '',
            stepName: '',
            limit: 100,
            sortDirection: 'DESC',
            queryType: 'HISTORICAL',
            logLevel: '',
            jobName: ''
        });

        // 重置時間範圍為過去24小時
        this.setQuickTimeRange(24);

        // 重置日誌級別選擇
        this.selectedLogLevels = this.logLevels.map(level => level.value);

        // 重置其他狀態
        this.selectedJobName = '';
        this.jobExecutions = [];

        // 應用重置後的過濾條件
        this.applyFilter();
    }

    /**
     * 快速過濾：只看錯誤
     */
    showErrorsOnly(): void {
        this.selectedLogLevels = ['ERROR'];
        this.filterForm.patchValue({ queryType: 'SEARCH' });
        this.applyFilter();
    }

    /**
     * 快速過濾：警告和錯誤
     */
    showWarningsAndErrors(): void {
        this.selectedLogLevels = ['WARN', 'ERROR'];
        this.filterForm.patchValue({ queryType: 'SEARCH' });
        this.applyFilter();
    }

    /**
     * 快速過濾：最近的日誌
     */
    showRecentLogs(): void {
        this.selectedLogLevels = this.logLevels.map(level => level.value);
        this.setQuickTimeRange(1); // 最近1小時
        this.filterForm.patchValue({ queryType: 'HISTORICAL' });
        this.applyFilter();
    }

    /**
     * 選擇全部執行記錄（清空 executionId 過濾）
     */
    selectAllExecutions(): void {
        this.filterForm.patchValue({ executionId: '' });
    }
} 