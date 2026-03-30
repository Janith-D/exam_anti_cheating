export interface Methodology {
  id?: number;
  name: string;
  description?: string;
  type: MethodologyType;
  monitoringLevel: MonitoringLevel;
  status: MethodologyStatus;
  alertThreshold?: number;
  createdBy?: string;
  createdAt?: string;
}

export enum MethodologyType {
  PROCTORED = 'PROCTORED',
  UNPROCTORED = 'UNPROCTORED',
  HYBRID = 'HYBRID',
  AI_ASSISTED = 'AI_ASSISTED'
}

export enum MonitoringLevel {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  STRICT = 'STRICT'
}

export enum MethodologyStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE'
}
