import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MethodologyService } from '../../Services/methodology.service';
import { AuthService } from '../../Services/auth.service.service';
import { Methodology, MethodologyType, MonitoringLevel, MethodologyStatus } from '../../models/methodology.model';

@Component({
  selector: 'app-methodology-management',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './methodology-management.component.html',
  styleUrl: './methodology-management.component.css'
})
export class MethodologyManagementComponent implements OnInit {
  methodologies: Methodology[] = [];
  loading = false;
  errorMessage = '';
  successMessage = '';

  showModal = false;
  editingMethodology: Methodology | null = null;
  methodologyForm: Methodology = this.getEmptyForm();

  MethodologyType = MethodologyType;
  MonitoringLevel = MonitoringLevel;
  MethodologyStatus = MethodologyStatus;

  methodologyTypes = Object.values(MethodologyType);
  monitoringLevels = Object.values(MonitoringLevel);

  constructor(
    private methodologyService: MethodologyService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (!this.authService.isAdmin) {
      this.router.navigate(['/login']);
      return;
    }
    this.loadMethodologies();
  }

  getEmptyForm(): Methodology {
    return {
      name: '',
      description: '',
      type: MethodologyType.PROCTORED,
      monitoringLevel: MonitoringLevel.MEDIUM,
      status: MethodologyStatus.ACTIVE,
      alertThreshold: 5
    };
  }

  loadMethodologies(): void {
    this.loading = true;
    this.methodologyService.getAllMethodologies().subscribe({
      next: (data) => {
        this.methodologies = data;
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = 'Failed to load methodologies: ' + (err.error?.error || err.message);
        this.loading = false;
      }
    });
  }

  openCreateModal(): void {
    this.editingMethodology = null;
    this.methodologyForm = this.getEmptyForm();
    this.showModal = true;
    this.errorMessage = '';
    this.successMessage = '';
  }

  openEditModal(methodology: Methodology): void {
    this.editingMethodology = methodology;
    this.methodologyForm = { ...methodology };
    this.showModal = true;
    this.errorMessage = '';
    this.successMessage = '';
  }

  closeModal(): void {
    this.showModal = false;
    this.editingMethodology = null;
    this.methodologyForm = this.getEmptyForm();
  }

  saveMethodology(): void {
    if (!this.methodologyForm.name?.trim()) {
      this.errorMessage = 'Methodology name is required.';
      return;
    }

    this.loading = true;
    if (this.editingMethodology?.id) {
      this.methodologyService.updateMethodology(this.editingMethodology.id, this.methodologyForm).subscribe({
        next: (res) => {
          this.successMessage = res.message || 'Methodology updated successfully';
          this.loading = false;
          this.closeModal();
          this.loadMethodologies();
        },
        error: (err) => {
          this.errorMessage = 'Failed to update methodology: ' + (err.error?.error || err.message);
          this.loading = false;
        }
      });
    } else {
      this.methodologyService.createMethodology(this.methodologyForm).subscribe({
        next: (res) => {
          this.successMessage = res.message || 'Methodology created successfully';
          this.loading = false;
          this.closeModal();
          this.loadMethodologies();
        },
        error: (err) => {
          this.errorMessage = 'Failed to create methodology: ' + (err.error?.error || err.message);
          this.loading = false;
        }
      });
    }
  }

  deleteMethodology(id: number): void {
    if (!confirm('Are you sure you want to delete this methodology?')) return;
    this.loading = true;
    this.methodologyService.deleteMethodology(id).subscribe({
      next: () => {
        this.successMessage = 'Methodology deleted successfully';
        this.loading = false;
        this.loadMethodologies();
      },
      error: (err) => {
        this.errorMessage = 'Failed to delete methodology: ' + (err.error?.error || err.message);
        this.loading = false;
      }
    });
  }

  toggleStatus(methodology: Methodology): void {
    if (!methodology.id) return;
    const action = methodology.status === MethodologyStatus.ACTIVE
      ? this.methodologyService.deactivateMethodology(methodology.id)
      : this.methodologyService.activateMethodology(methodology.id);

    this.loading = true;
    action.subscribe({
      next: (res) => {
        this.successMessage = res.message || 'Status updated';
        this.loading = false;
        this.loadMethodologies();
      },
      error: (err) => {
        this.errorMessage = 'Failed to update status: ' + (err.error?.error || err.message);
        this.loading = false;
      }
    });
  }

  getTypeBadge(type: MethodologyType): string {
    switch (type) {
      case MethodologyType.PROCTORED: return 'primary';
      case MethodologyType.UNPROCTORED: return 'secondary';
      case MethodologyType.HYBRID: return 'info';
      case MethodologyType.AI_ASSISTED: return 'warning';
      default: return 'secondary';
    }
  }

  getLevelBadge(level: MonitoringLevel): string {
    switch (level) {
      case MonitoringLevel.LOW: return 'success';
      case MonitoringLevel.MEDIUM: return 'info';
      case MonitoringLevel.HIGH: return 'warning';
      case MonitoringLevel.STRICT: return 'danger';
      default: return 'secondary';
    }
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
