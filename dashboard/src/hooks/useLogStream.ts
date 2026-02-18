import { useEffect, useRef, useState, useCallback } from 'react';
import type { Log } from '../types/log';

const WS_URL = 'ws://localhost:8083/ws/logs';
const MAX_LOGS = 100;

interface UseLogStreamResult {
  logs: Log[];
  isConnected: boolean;
  error: string | null;
  clearLogs: () => void;
}

export function useLogStream(): UseLogStreamResult {
  const [logs, setLogs] = useState<Log[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<number | null>(null);

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      return;
    }

    const ws = new WebSocket(WS_URL);

    ws.onopen = () => {
      setIsConnected(true);
      setError(null);
    };

    ws.onmessage = (event) => {
      try {
        const log: Log = JSON.parse(event.data);
        setLogs((prev) => [log, ...prev].slice(0, MAX_LOGS));
      } catch (e) {
        console.error('Failed to parse log:', e);
      }
    };

    ws.onclose = () => {
      setIsConnected(false);
      // Reconnect after 3 seconds
      reconnectTimeoutRef.current = window.setTimeout(() => {
        connect();
      }, 3000);
    };

    ws.onerror = () => {
      setError('WebSocket connection error');
      setIsConnected(false);
    };

    wsRef.current = ws;
  }, []);

  useEffect(() => {
    connect();

    return () => {
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      wsRef.current?.close();
    };
  }, [connect]);

  const clearLogs = useCallback(() => {
    setLogs([]);
  }, []);

  return { logs, isConnected, error, clearLogs };
}
