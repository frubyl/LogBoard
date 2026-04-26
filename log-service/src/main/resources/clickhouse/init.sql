CREATE DATABASE IF NOT EXISTS logboard_logs;

CREATE TABLE IF NOT EXISTS logboard_logs.logs
(
    id           UUID,
    project_id   UUID,
    ingestion_id UUID,
    level        String,
    message      String,
    timestamp    DateTime64(3, 'UTC')
) ENGINE = MergeTree()
      PARTITION BY toYYYYMM(timestamp)
      ORDER BY (project_id, timestamp, id)
      TTL toDateTime(timestamp) + INTERVAL 90 DAY DELETE;
