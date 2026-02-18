import { useLogStream } from '../hooks/useLogStream';

function formatTimestamp(ts: string): string {
  return new Date(ts).toLocaleTimeString();
}

function SeverityBadge({ severity }: { severity: string }) {
  const colors: Record<string, string> = {
    INFO: 'bg-blue-100 text-blue-800',
    WARN: 'bg-yellow-100 text-yellow-800',
    ERROR: 'bg-red-100 text-red-800',
  };

  return (
    <span className={`px-2 py-0.5 text-xs font-medium rounded ${colors[severity] || 'bg-gray-100 text-gray-800'}`}>
      {severity}
    </span>
  );
}

export function LiveLogs() {
  const { logs, isConnected, error, clearLogs } = useLogStream();

  return (
    <div className="bg-gray-900 rounded-lg shadow overflow-hidden">
      <div className="bg-gray-800 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h3 className="text-white font-medium">Live Logs</h3>
          <span className={`flex items-center gap-1.5 text-xs ${isConnected ? 'text-green-400' : 'text-red-400'}`}>
            <span className={`w-2 h-2 rounded-full ${isConnected ? 'bg-green-400 animate-pulse' : 'bg-red-400'}`}></span>
            {isConnected ? 'Connected' : 'Disconnected'}
          </span>
        </div>
        <button
          onClick={clearLogs}
          className="text-gray-400 hover:text-white text-sm"
        >
          Clear
        </button>
      </div>

      {error && (
        <div className="bg-red-900 text-red-200 px-4 py-2 text-sm">
          {error}
        </div>
      )}

      <div className="h-96 overflow-y-auto font-mono text-sm">
        {logs.length === 0 ? (
          <div className="text-gray-500 text-center py-8">
            Waiting for logs...
          </div>
        ) : (
          <div className="divide-y divide-gray-800">
            {logs.map((log, index) => (
              <div key={`${log.timestamp}-${index}`} className="px-4 py-2 hover:bg-gray-800">
                <div className="flex items-center gap-3">
                  <span className="text-gray-500">{formatTimestamp(log.timestamp)}</span>
                  <span className="text-blue-400">{log.serviceName}</span>
                  <SeverityBadge severity={log.severity} />
                </div>
                <div className="text-gray-300 mt-1 break-all">{log.message}</div>
                {log.traceId && (
                  <div className="text-gray-600 text-xs mt-1">trace: {log.traceId}</div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
