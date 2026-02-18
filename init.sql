-- Database schema for Distributed Logging System

-- Logs table
CREATE TABLE IF NOT EXISTS logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name VARCHAR(100) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    message TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    trace_id VARCHAR(50),
    host VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_logs_service_timestamp ON logs(service_name, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_logs_severity_timestamp ON logs(severity, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_logs_trace_id ON logs(trace_id) WHERE trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_logs_timestamp ON logs(timestamp DESC);

-- Processed IDs table (for idempotency)
CREATE TABLE IF NOT EXISTS processed_ids (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    processed_at TIMESTAMPTZ DEFAULT NOW()
);

-- Function to auto-cleanup old processed_ids (older than 24 hours)
CREATE OR REPLACE FUNCTION cleanup_old_processed_ids()
RETURNS void AS $$
BEGIN
    DELETE FROM processed_ids WHERE processed_at < NOW() - INTERVAL '24 hours';
END;
$$ LANGUAGE plpgsql;

-- Grant permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
