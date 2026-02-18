import { useState, useCallback } from 'react';
import { LogFilters } from './components/LogFilters';
import { LogTable } from './components/LogTable';
import { LiveLogs } from './components/LiveLogs';
import { MetricsPanel } from './components/MetricsPanel';
import { fetchLogs } from './api/logs';
import type { Log, LogFilters as LogFiltersType, PagedResponse } from './types/log';

type Tab = 'search' | 'live';

function App() {
  const [activeTab, setActiveTab] = useState<Tab>('search');
  const [filters, setFilters] = useState<LogFiltersType>({ size: 20 });
  const [logs, setLogs] = useState<PagedResponse<Log> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSearch = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchLogs(filters);
      setLogs(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to fetch logs');
    } finally {
      setLoading(false);
    }
  }, [filters]);

  const handlePageChange = useCallback(async (page: number) => {
    setFilters((prev) => ({ ...prev, page }));
    setLoading(true);
    setError(null);
    try {
      const data = await fetchLogs({ ...filters, page });
      setLogs(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to fetch logs');
    } finally {
      setLoading(false);
    }
  }, [filters]);

  return (
    <div className="min-h-screen bg-gray-100">
      {/* Header */}
      <header className="bg-white shadow">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <h1 className="text-2xl font-bold text-gray-900">Log Dashboard</h1>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 py-6">
        {/* Metrics */}
        <div className="mb-6">
          <MetricsPanel />
        </div>

        {/* Tabs */}
        <div className="border-b border-gray-200 mb-6">
          <nav className="flex gap-4">
            <button
              onClick={() => setActiveTab('search')}
              className={`py-2 px-1 border-b-2 font-medium text-sm transition-colors ${
                activeTab === 'search'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              Search Logs
            </button>
            <button
              onClick={() => setActiveTab('live')}
              className={`py-2 px-1 border-b-2 font-medium text-sm transition-colors ${
                activeTab === 'live'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              Live Logs
            </button>
          </nav>
        </div>

        {/* Tab Content */}
        {activeTab === 'search' ? (
          <div>
            <LogFilters
              filters={filters}
              onChange={setFilters}
              onSearch={handleSearch}
            />

            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded mb-4">
                {error}
              </div>
            )}

            <LogTable
              data={logs}
              loading={loading}
              onPageChange={handlePageChange}
            />
          </div>
        ) : (
          <LiveLogs />
        )}
      </main>
    </div>
  );
}

export default App;
