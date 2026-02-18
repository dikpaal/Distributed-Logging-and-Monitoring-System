export type Severity = 'INFO' | 'WARN' | 'ERROR';

export interface Log {
  id: string;
  serviceName: string;
  severity: Severity;
  message: string;
  timestamp: string;
  traceId?: string;
  host?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface SeverityCount {
  severity: string;
  count: number;
}

export interface ServiceLogCount {
  serviceName: string;
  count: number;
}

export interface LogFilters {
  serviceName?: string;
  severity?: Severity;
  traceId?: string;
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
}
