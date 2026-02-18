import type { Log, PagedResponse } from '../types/log';

interface Props {
  data: PagedResponse<Log> | null;
  loading: boolean;
  onPageChange: (page: number) => void;
}

function formatTimestamp(ts: string): string {
  return new Date(ts).toLocaleString();
}

function SeverityBadge({ severity }: { severity: string }) {
  const colors: Record<string, string> = {
    INFO: 'bg-blue-100 text-blue-800',
    WARN: 'bg-yellow-100 text-yellow-800',
    ERROR: 'bg-red-100 text-red-800',
  };

  return (
    <span className={`px-2 py-1 text-xs font-medium rounded ${colors[severity] || 'bg-gray-100 text-gray-800'}`}>
      {severity}
    </span>
  );
}

export function LogTable({ data, loading, onPageChange }: Props) {
  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow p-8 text-center">
        <div className="animate-spin h-8 w-8 border-4 border-blue-500 border-t-transparent rounded-full mx-auto"></div>
        <p className="mt-4 text-gray-500">Loading logs...</p>
      </div>
    );
  }

  if (!data || data.content.length === 0) {
    return (
      <div className="bg-white rounded-lg shadow p-8 text-center text-gray-500">
        No logs found
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow overflow-hidden">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Timestamp</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Service</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Severity</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Message</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Trace ID</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {data.content.map((log) => (
              <tr key={log.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 text-sm text-gray-500 whitespace-nowrap">
                  {formatTimestamp(log.timestamp)}
                </td>
                <td className="px-4 py-3 text-sm font-medium text-gray-900">
                  {log.serviceName}
                </td>
                <td className="px-4 py-3">
                  <SeverityBadge severity={log.severity} />
                </td>
                <td className="px-4 py-3 text-sm text-gray-700 max-w-md truncate">
                  {log.message}
                </td>
                <td className="px-4 py-3 text-sm text-gray-500 font-mono">
                  {log.traceId || '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      <div className="bg-gray-50 px-4 py-3 flex items-center justify-between border-t border-gray-200">
        <div className="text-sm text-gray-700">
          Showing page {data.page + 1} of {data.totalPages} ({data.totalElements} total logs)
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => onPageChange(data.page - 1)}
            disabled={data.first}
            className="px-3 py-1 border rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-100"
          >
            Previous
          </button>
          <button
            onClick={() => onPageChange(data.page + 1)}
            disabled={data.last}
            className="px-3 py-1 border rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-100"
          >
            Next
          </button>
        </div>
      </div>
    </div>
  );
}
