import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { LogFilter } from '../../models/batch-log.model';

@Component({
    selector: 'app-log-filter',
    templateUrl: './log-filter.component.html',
    styleUrls: ['./log-filter.component.scss']
})
export class LogFilterComponent implements OnInit {
    @Output() filterChange = new EventEmitter<LogFilter>();

    filterForm: FormGroup;
    logLevels = ['ALL', 'DEBUG', 'INFO', 'WARN', 'ERROR'];

    constructor(private fb: FormBuilder) {
        this.filterForm = this.fb.group({
            startTime: [''],
            endTime: [''],
            logLevel: ['ALL'],
            keyword: [''],
            executionId: [''],
            jobName: ['']
        });
    }

    ngOnInit(): void {
        // 設定預設時間範圍為過去24小時
        const now = new Date();
        const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);

        this.filterForm.patchValue({
            startTime: this.formatDateTime(yesterday),
            endTime: this.formatDateTime(now)
        });

        // 監聽表單變化
        this.filterForm.valueChanges.subscribe(() => {
            this.applyFilter();
        });
    }

    private formatDateTime(date: Date): string {
        return date.toISOString().slice(0, 16);
    }

    applyFilter(): void {
        const formValue = this.filterForm.value;
        const filter: LogFilter = {
            startTime: formValue.startTime || undefined,
            endTime: formValue.endTime || undefined,
            logLevel: formValue.logLevel === 'ALL' ? undefined : formValue.logLevel,
            keyword: formValue.keyword || undefined,
            executionId: formValue.executionId || undefined,
            jobName: formValue.jobName || undefined
        };

        this.filterChange.emit(filter);
    }

    resetFilter(): void {
        const now = new Date();
        const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);

        this.filterForm.reset({
            startTime: this.formatDateTime(yesterday),
            endTime: this.formatDateTime(now),
            logLevel: 'ALL',
            keyword: '',
            executionId: '',
            jobName: ''
        });
    }

    setQuickTimeRange(hours: number): void {
        const now = new Date();
        const past = new Date(now.getTime() - hours * 60 * 60 * 1000);

        this.filterForm.patchValue({
            startTime: this.formatDateTime(past),
            endTime: this.formatDateTime(now)
        });
    }
} 