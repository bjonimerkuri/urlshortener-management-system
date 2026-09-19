import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged, forkJoin } from 'rxjs';
import { AdminService } from '../../core/api/admin.service';
import { errorMessage } from '../../core/http/api-error';
import { ShortUrlResponse, UrlStats, UrlStatus } from '../../core/models/api.models';
import { NotificationService } from '../../core/notification.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { RelativeTimePipe } from '../../shared/relative-time.pipe';
import { StatCardComponent } from '../../shared/stat-card.component';
import { UrlStatusChipComponent } from '../../shared/url-status-chip.component';

interface Year {
  year: string;
}

@Component({
  selector: 'app-admin-urls',
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    StatCardComponent,
    UrlStatusChipComponent,
    RelativeTimePipe,
  ],
  templateUrl: './admin-urls.component.html',
  styleUrl: './admin-urls.component.scss',
})
export class AdminUrlsComponent {
  private readonly admin = inject(AdminService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  protected readonly displayedColumns = ['shortCode', 'originalUrl', 'ownerEmail', 'status', 'clickCount', 'actions'];

  protected readonly rows = signal<readonly ShortUrlResponse[]>([]);
  protected readonly stats = signal<UrlStats | null>(null);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly status = new FormControl<UrlStatus | ''>('', { nonNullable: true });
  protected readonly ownerEmail = new FormControl('', { nonNullable: true });
  protected readonly years

  private page = 0;
  private size = 10;
  private sort = 'createdAt,desc';

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.reload(true));
    this.ownerEmail.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.reload(true));
    this.status.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.reload(true));

    this.reload();
  }

  protected reload(resetPage = false): void {
    if (resetPage) {
      this.page = 0;
    }
    this.loading.set(true);
    this.error.set(null);

    forkJoin({
      page: this.admin.listUrls({
        page: this.page,
        size: this.size,
        sort: this.sort,
        search: this.search.value.trim(),
        status: this.status.value,
        ownerEmail: this.ownerEmail.value.trim(),
      }),
      stats: this.admin.stats(),
    }).subscribe({
      next: ({ page, stats }) => {
        this.rows.set(page.content);
        this.total.set(page.totalElements);
        this.stats.set(stats);
        this.loading.set(false);
      },
      error: (cause: unknown) => {
        this.error.set(errorMessage(cause, 'Could not load the URL list.'));
        this.loading.set(false);
      },
    });
  }

  protected onPage(event: PageEvent): void {
    this.page = event.pageIndex;
    this.size = event.pageSize;
    this.reload();
  }

  protected onSort(event: Sort): void {
    this.sort = event.direction ? `${event.active},${event.direction}` : 'createdAt,desc';
    this.reload(true);
  }

  protected get pageIndex(): number {
    return this.page;
  }

  protected get pageSize(): number {
    return this.size;
  }

  protected remove(url: ShortUrlResponse): void {
    this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
        data: {
          title: 'Delete this short URL?',
          message: `/${url.shortCode}, owned by ${url.ownerEmail}, will stop working immediately. This cannot be undone.`,
          confirmLabel: 'Delete',
          destructive: true,
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.admin.deleteUrl(url.id).subscribe({
          next: () => {
            this.notifications.success('Short URL deleted.');
            this.reload();
          },
          error: (cause: unknown) => this.notifications.error(errorMessage(cause, 'Could not delete this URL.')),
        });
      });
  }

  protected clearFilters(): void {
    this.search.setValue('', { emitEvent: false });
    this.status.setValue('', { emitEvent: false });
    this.ownerEmail.setValue('', { emitEvent: false });
    this.reload(true);
  }

  protected get hasFilters(): boolean {
    return this.search.value.trim().length > 0 || this.status.value !== '' || this.ownerEmail.value.trim().length > 0;
  }
}
