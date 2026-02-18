import { useEffect, useState } from 'react';
import { fetchSeverityCounts, fetchServiceCounts } from '../api/logs';
import type { SeverityCount, ServiceLogCount } from '../types/log';

export function MetricsPanel() {
  const [severityCounts, setSeverityCounts] = useState<SeverityCount[]>([]);
  const [serviceCounts, setServiceCounts] = useState<ServiceLogCount[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadMetrics() {
      try {
        const [severity, services] = await Promise.all([
          fetchSeverityCounts(),
          fetchServiceCounts(),
        ]);
        setSeverityCounts(severity);
        setServiceCounts(services);
      } catch (error) {
        console.error('Failed to load metrics:', error);
      } finally {
        setLoading(false);
      }
    }

    loadMetrics();
    const interval = setInterval(loadMetrics, 30000); // Refresh every 30s

    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow p-4">
        <div className="animate-pulse space-y-3">
          <div className="h-4 bg-gray-200 rounded w-1/3"></div>
          <div className="h-8 bg-gray-200 rounded"></div>
          <div className="h-8 bg-gray-200 rounded"></div>
        </div>
      </div>
    );
  }

  const severityColors: Record<string, string> = {
    INFO: 'text-blue-600',
    WARN: 'text-yellow-600',
    ERROR: 'text-red-600',
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {/* Severity Distribution */}
      <div className="bg-white rounded-lg shadow p-4">
        <h3 className="text-lg font-medium text-gray-900 mb-4">Severity Distribution</h3>
        <div className="space-y-3">
          {severityCounts.map((item) => (
            <div key={item.severity} className="flex items-center justify-between">
              <span className={`font-medium ${severityColors[item.severity] || 'text-gray-600'}`}>
                {item.severity}
              </span>
              <span className="text-gray-900 font-semibold">{item.count.toLocaleString()}</span>
            </div>
          ))}
          {severityCounts.length === 0 && (
            <p className="text-gray-500 text-sm">No data available</p>
          )}
        </div>
      </div>

      {/* Service Distribution */}
      <div className="bg-white rounded-lg shadow p-4">
        <h3 className="text-lg font-medium text-gray-900 mb-4">Logs by Service</h3>
        <div className="space-y-3">
          {serviceCounts.slice(0, 5).map((item) => (
            <div key={item.serviceName} className="flex items-center justify-between">
              <span className="text-gray-700">{item.serviceName}</span>
              <span className="text-gray-900 font-semibold">{item.count.toLocaleString()}</span>
            </div>
          ))}
          {serviceCounts.length === 0 && (
            <p className="text-gray-500 text-sm">No data available</p>
          )}
        </div>
      </div>
    </div>
  );
}
