import type { LogFilters as LogFiltersType, Severity } from '../types/log';

interface Props {
  filters: LogFiltersType;
  onChange: (filters: LogFiltersType) => void;
  onSearch: () => void;
}

const SEVERITIES: Severity[] = ['INFO', 'WARN', 'ERROR'];

export function LogFilters({ filters, onChange, onSearch }: Props) {
  return (
    <div className="bg-white p-4 rounded-lg shadow mb-4">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Service
          </label>
          <input
            type="text"
            value={filters.serviceName || ''}
            onChange={(e) => onChange({ ...filters, serviceName: e.target.value || undefined })}
            placeholder="e.g., user-service"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Severity
          </label>
          <select
            value={filters.severity || ''}
            onChange={(e) => onChange({ ...filters, severity: (e.target.value as Severity) || undefined })}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">All</option>
            {SEVERITIES.map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Trace ID
          </label>
          <input
            type="text"
            value={filters.traceId || ''}
            onChange={(e) => onChange({ ...filters, traceId: e.target.value || undefined })}
            placeholder="e.g., abc-123"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div className="flex items-end">
          <button
            onClick={onSearch}
            className="w-full bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 transition-colors"
          >
            Search
          </button>
        </div>
      </div>
    </div>
  );
}
