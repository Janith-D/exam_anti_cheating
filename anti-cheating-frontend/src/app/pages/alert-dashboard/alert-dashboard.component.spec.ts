import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AlertDashboardComponent } from './alert-dashboard.component';

describe('AlertDashboardComponent', () => {
  let component: AlertDashboardComponent;
  let fixture: ComponentFixture<AlertDashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AlertDashboardComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AlertDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
