import { Component, OnInit, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { BatchService } from '../../services/batch.service';
import { BatchJob, BatchExecution } from '../../models/batch-log.model';
import { LogDialogComponent } from '../log-dialog/log-dialog.component';

@Component({
    selector: 'app-batch-control',
    templateUrl: './batch-control.component.html',
    styleUrls: ['./batch-control.component.scss']
})
export class BatchControlComponent implements OnInit {
    @ViewChild(MatPaginator) paginator!: MatPaginator;
    @ViewChild(MatSort) sort!: MatSort;

    displayedColumns: string[] = ['displayName', 'category', 'status', 'lastExecutionTime', 'actions'];
    dataSource = new MatTableDataSource<BatchJob>();

    isLoading = false;
    error = '';
    executingJobs = new Set<string>();

    constructor(
        private batchService: BatchService,
        private dialog: MatDialog,
        private snackBar: MatSnackBar
    ) { }

    ngOnInit(): void {
        this.loadBatchJobs();
    }

    ngAfterViewInit(): void {
        this.dataSource.paginator = this.paginator;
        this.dataSource.sort = this.sort;
    }

    loadBatchJobs(): void {
        this.isLoading = true;
        this.error = '';

        this.batchService.getBatchJobs().subscribe({
            next: (jobs) => {
                this.dataSource.data = jobs;
                this.isLoading = false;
            },
            error: (error) => {
                this.error = `載入批次作業列表失敗: ${error.error || error.message}`;
                this.isLoading = false;
            }
        });
    }

    executeJob(job: BatchJob): void {
        if (this.executingJobs.has(job.id)) {
            return;
        }

        this.executingJobs.add(job.id);

        this.batchService.runBatchJob(job.id).subscribe({
            next: (execution: BatchExecution) => {
                this.executingJobs.delete(job.id);

                // 更新作業狀態
                const updatedJob = this.dataSource.data.find(j => j.id === job.id);
                if (updatedJob) {
                    updatedJob.status = 'RUNNING';
                    updatedJob.lastExecutionId = execution.executionId;
                    updatedJob.lastExecutionTime = execution.startTime;
                }

                this.snackBar.open(
                    `批次作業 "${job.displayName}" 已成功啟動！執行代號: ${execution.executionId}`,
                    '關閉',
                    { duration: 5000 }
                );

                // 重新整理資料
                this.dataSource._updateChangeSubscription();
            },
            error: (error) => {
                this.executingJobs.delete(job.id);
                this.snackBar.open(
                    `執行批次作業 "${job.displayName}" 失敗: ${error.error || error.message}`,
                    '關閉',
                    { duration: 5000, panelClass: ['error-snackbar'] }
                );
            }
        });
    }

    viewLogs(job: BatchJob): void {
        const dialogRef = this.dialog.open(LogDialogComponent, {
            data: {
                job: job,
                executionId: job.lastExecutionId
            },
            width: '90vw',
            maxWidth: '1200px',
            height: '90vh',
            panelClass: 'log-dialog-panel'
        });

        dialogRef.afterClosed().subscribe(() => {
            // 彈窗關閉後可以執行一些清理工作
        });
    }

    isJobExecuting(jobId: string): boolean {
        return this.executingJobs.has(jobId);
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

    getStatusClass(status: string): string {
        return `status-${status.toLowerCase()}`;
    }

    formatDateTime(dateTime?: string): string {
        if (!dateTime) return '-';
        return new Date(dateTime).toLocaleString('zh-TW');
    }

    refreshJobs(): void {
        this.loadBatchJobs();
    }

    clearError(): void {
        this.error = '';
    }

    getJobCountByStatus(status: string): number {
        return this.dataSource.data.filter(job => job.status === status).length;
    }
} 