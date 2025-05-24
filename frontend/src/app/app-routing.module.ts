import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { BatchControlComponent } from './components/batch-control/batch-control.component';
import { LogViewerComponent } from './components/log-viewer/log-viewer.component';

const routes: Routes = [
    { path: '', redirectTo: '/batch-control', pathMatch: 'full' },
    { path: 'batch-control', component: BatchControlComponent },
    { path: 'log-viewer', component: LogViewerComponent }
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { } 