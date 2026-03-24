-- SPDX-FileCopyrightText: 2026 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0

SET search_path = intimev2, public;

-- ============================================================
-- timeseries_stats: one row per timeseries, updated ~weekly
-- ============================================================
CREATE TABLE IF NOT EXISTS intimev2.timeseries_stats (
    timeseries_id    int4        NOT NULL,
    first_timestamp  timestamp   NULL,   -- oldest record in *history
    last_timestamp   timestamp   NULL,   -- latest record in current measurement table
    record_count     int8        NULL,   -- rows in *history
    avg_value_size   float8      NULL,   -- bytes; NULL for double (always 8 B, not interesting)
    stats_updated_at timestamp   NOT NULL DEFAULT now(),
    CONSTRAINT timeseries_stats_pkey PRIMARY KEY (timeseries_id),
    CONSTRAINT fk_timeseries_stats_timeseries
        FOREIGN KEY (timeseries_id) REFERENCES intimev2.timeseries(id)
);

-- Used by the job to find the most-stale rows efficiently
CREATE INDEX IF NOT EXISTS idx_timeseries_stats_updated_at
    ON intimev2.timeseries_stats (stats_updated_at);
    
CREATE INDEX IF NOT EXISTS idx_timeseries_stats_timeseries
    ON intimev2.timeseries_stats (timeseries_id);

-- ============================================================
-- refresh_timeseries_stats(batch_size, avg_sample_size)
--
-- Call this on a schedule (e.g. daily via pg_cron).
-- Each call processes up to `p_batch_size` timeseries —
-- always the ones whose stats are oldest / missing first.
--
-- Performance strategy for deep/fragmented timeseries:
--  - first_timestamp: LATERAL + LIMIT 1 ORDER BY ASC  → single index seek, O(log n)
--  - last_timestamp:  direct join to measurement table → unique-index lookup, O(1)
--  - record_count:    pg_class.reltuples of the partition table → O(1), approximate
--  - avg_value_size:  LATERAL sample of p_avg_sample_size recent rows → bounded cost
-- ============================================================
CREATE OR REPLACE FUNCTION intimev2.refresh_timeseries_stats(
    p_batch_size      int DEFAULT 200,
    p_avg_sample_size int DEFAULT 500   -- rows sampled for avg_value_size
)
RETURNS int       -- number of timeseries actually processed
LANGUAGE plpgsql
AS $$
DECLARE
    v_processed int;
BEGIN
    -- Materialise the stale batch once; referenced three times below.
    -- partition_id is included so joins on history tables carry the
    -- partition key, enabling Postgres to prune to a single partition.
    CREATE TEMP TABLE _ts_batch ON COMMIT DROP AS
    SELECT t.id AS timeseries_id, t.value_table, t.partition_id
    FROM   intimev2.timeseries t
    LEFT JOIN intimev2.timeseries_stats s ON s.timeseries_id = t.id
    WHERE  s.timeseries_id IS NULL
        OR s.stats_updated_at < now() - INTERVAL '7 days'
    ORDER  BY COALESCE(s.stats_updated_at, '1970-01-01'::timestamp) ASC
    LIMIT  p_batch_size;

    INSERT INTO intimev2.timeseries_stats
        (timeseries_id, first_timestamp, last_timestamp,
         record_count, avg_value_size, stats_updated_at)

    -- doubles
    SELECT
        b.timeseries_id,
        first_h.ts                                                AS first_timestamp,
        m.timestamp                                               AS last_timestamp,
        (SELECT reltuples::int8 FROM pg_class
          WHERE oid = ('intimev2.measurementhistory_' || b.partition_id)::regclass) AS record_count,
        NULL::float8                                              AS avg_value_size,
        now()
    FROM _ts_batch b
    JOIN intimev2.measurement m ON m.timeseries_id = b.timeseries_id
    JOIN LATERAL (
        SELECT h.timestamp AS ts
        FROM   intimev2.measurementhistory h
        WHERE  h.timeseries_id = b.timeseries_id
          AND  h.partition_id  = b.partition_id
        ORDER  BY h.timestamp ASC
        LIMIT  1
    ) first_h ON true
    WHERE b.value_table = 'measurement'

    UNION ALL

    -- strings
    SELECT
        b.timeseries_id,
        first_h.ts,
        m.timestamp,
        (SELECT reltuples::int8 FROM pg_class
          WHERE oid = ('intimev2.measurementstringhistory_' || b.partition_id)::regclass),
        avg_s.avg_size,
        now()
    FROM _ts_batch b
    JOIN intimev2.measurementstring m ON m.timeseries_id = b.timeseries_id
    JOIN LATERAL (
        SELECT h.timestamp AS ts
        FROM   intimev2.measurementstringhistory h
        WHERE  h.timeseries_id = b.timeseries_id
          AND  h.partition_id  = b.partition_id
        ORDER  BY h.timestamp ASC
        LIMIT  1
    ) first_h ON true
    JOIN LATERAL (
        SELECT AVG(length(h.string_value)) AS avg_size
        FROM (
            SELECT h.string_value
            FROM   intimev2.measurementstringhistory h
            WHERE  h.timeseries_id = b.timeseries_id
              AND  h.partition_id  = b.partition_id
            ORDER  BY h.timestamp DESC
            LIMIT  p_avg_sample_size
        ) h
    ) avg_s ON true
    WHERE b.value_table = 'measurementstring'

    UNION ALL

    -- json
    SELECT
        b.timeseries_id,
        first_h.ts,
        m.timestamp,
        (SELECT reltuples::int8 FROM pg_class
          WHERE oid = ('intimev2.measurementjsonhistory_' || b.partition_id)::regclass),
        avg_s.avg_size,
        now()
    FROM _ts_batch b
    JOIN intimev2.measurementjson m ON m.timeseries_id = b.timeseries_id
    JOIN LATERAL (
        SELECT h.timestamp AS ts
        FROM   intimev2.measurementjsonhistory h
        WHERE  h.timeseries_id = b.timeseries_id
          AND  h.partition_id  = b.partition_id
        ORDER  BY h.timestamp ASC
        LIMIT  1
    ) first_h ON true
    JOIN LATERAL (
        SELECT AVG(pg_column_size(h.json_value)) AS avg_size
        FROM (
            SELECT h.json_value
            FROM   intimev2.measurementjsonhistory h
            WHERE  h.timeseries_id = b.timeseries_id
              AND  h.partition_id  = b.partition_id
            ORDER  BY h.timestamp DESC
            LIMIT  p_avg_sample_size
        ) h
    ) avg_s ON true
    WHERE b.value_table = 'measurementjson'

    ON CONFLICT (timeseries_id) DO UPDATE SET
        first_timestamp  = EXCLUDED.first_timestamp,
        last_timestamp   = EXCLUDED.last_timestamp,
        record_count     = EXCLUDED.record_count,
        avg_value_size   = EXCLUDED.avg_value_size,
        stats_updated_at = EXCLUDED.stats_updated_at;

    GET DIAGNOSTICS v_processed = ROW_COUNT;
    RETURN v_processed;
END;
$$;


-- ============================================================
-- Schedule with pg_cron
--
-- Run once as a superuser after enabling the extension:
--   CREATE EXTENSION IF NOT EXISTS pg_cron;
--
-- Then register the job:
--
--   SELECT cron.schedule(
--       'refresh-timeseries-stats',   -- job name (unique)
--       '0 3 * * *',                  -- daily at 03:00
--       $$SELECT intimev2.refresh_timeseries_stats(200, 500)$$
--   );
--
-- Useful management queries:
--   SELECT * FROM cron.job;                                  -- list jobs
--   SELECT * FROM cron.job_run_details ORDER BY start_time DESC LIMIT 20; -- history
--   SELECT cron.unschedule('refresh-timeseries-stats');      -- remove job
-- ============================================================
