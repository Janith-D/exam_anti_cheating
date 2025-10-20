import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProctorDashboardComponent } from './proctor-dashboard.component';

describe('ProctorDashboardComponent', () => {
  let component: ProctorDashboardComponent;
  let fixture: ComponentFixture<ProctorDashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProctorDashboardComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProctorDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
