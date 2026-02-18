import type { Log, LogFilters, PagedResponse, ServiceLogCount, SeverityCount } from '../types/log';

const API_BASE = 'http://localhost:8083/api/v1';

export async function fetchLogs(filters: LogFilters = {}): Promise<PagedResponse<Log>> {
  const params = new URLSearchParams();

  if (filters.serviceName) params.append('serviceName', filters.serviceName);
  if (filters.severity) params.append('severity', filters.severity);
  if (filters.traceId) params.append('traceId', filters.traceId);
  if (filters.startTime) params.append('startTime', filters.startTime);
  if (filters.endTime) params.append('endTime', filters.endTime);
  if (filters.page !== undefined) params.append('page', String(filters.page));
  if (filters.size !== undefined) params.append('size', String(filters.size));

  const response = await fetch(`${API_BASE}/logs?${params.toString()}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch logs: ${response.statusText}`);
  }
  return response.json();
}

export async function fetchLogById(id: string): Promise<Log> {
  const response = await fetch(`${API_BASE}/logs/${id}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch log: ${response.statusText}`);
  }
  return response.json();
}

export async function fetchLogsByTraceId(traceId: string): Promise<Log[]> {
  const response = await fetch(`${API_BASE}/logs/trace/${traceId}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch logs by trace: ${response.statusText}`);
  }
  return response.json();
}

export async function fetchSeverityCounts(serviceName?: string): Promise<SeverityCount[]> {
  const params = serviceName ? `?serviceName=${serviceName}` : '';
  const response = await fetch(`${API_BASE}/metrics/counts${params}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch severity counts: ${response.statusText}`);
  }
  return response.json();
}

export async function fetchServiceCounts(): Promise<ServiceLogCount[]> {
  const response = await fetch(`${API_BASE}/metrics/services`);
  if (!response.ok) {
    throw new Error(`Failed to fetch service counts: ${response.statusText}`);
  }
  return response.json();
}
