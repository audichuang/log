import { Component, OnDestroy } from '@angular/core';
import { BatchService } from './services/batch.service';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnDestroy {
    title = '批次作業日誌管理系統';

    constructor(private batchService: BatchService) {
        // 監聽頁面關閉事件
        window.addEventListener('beforeunload', this.handleBeforeUnload.bind(this));
        window.addEventListener('unload', this.handleUnload.bind(this));
    }

    ngOnDestroy(): void {
        this.cleanupConnections();
    }

    private handleBeforeUnload(event: BeforeUnloadEvent): void {
        // 頁面即將關閉時清理SSE連線
        this.cleanupConnections();
    }

    private handleUnload(event: Event): void {
        // 頁面完全關閉時的最後清理
        this.cleanupConnections();
    }

    private cleanupConnections(): void {
        // 斷開所有SSE連線
        this.batchService.disconnectLogStream();
        console.log('應用程式關閉，SSE連線已清理');
    }
} 