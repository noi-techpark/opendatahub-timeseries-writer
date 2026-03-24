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
-- refresh_timeseries_stats(batch_size)
--
-- Call this on a schedule (e.g. daily via pg_cron).
-- Each call processes up to `p_batch_size` timeseries —
-- always the ones whose stats are oldest / missing first.
-- With 200 rows/day every timeseries is refreshed in at most
-- ceil(total_timeseries / 200) days, typically well under a week.
-- ============================================================
CREATE OR REPLACE FUNCTION intimev2.refresh_timeseries_stats(
    p_batch_size int DEFAULT 200
)
RETURNS int       -- number of timeseries actually processed
LANGUAGE plpgsql
AS $$
DECLARE
    v_ts        RECORD;
    v_first_ts  timestamp;
    v_last_ts   timestamp;
    v_count     int8;
    v_avg_size  float8;
    v_processed int := 0;
BEGIN
    FOR v_ts IN
        -- Pick the batch that needs the most urgent refresh.
        -- Rows with no stats entry sort first (epoch fallback).
        SELECT t.id, t.value_table
        FROM   intimev2.timeseries t
        LEFT JOIN intimev2.timeseries_stats s ON s.timeseries_id = t.id
        WHERE  s.timeseries_id IS NULL
            OR s.stats_updated_at < now() - INTERVAL '7 days'
        ORDER  BY COALESCE(s.stats_updated_at, '1970-01-01'::timestamp) ASC
        LIMIT  p_batch_size
    LOOP
        v_first_ts := NULL;
        v_last_ts  := NULL;
        v_count    := 0;
        v_avg_size := NULL;

        -- Each branch uses the existing (timeseries_id, timestamp) index,
        -- so MIN/COUNT are an index-only scan on the relevant partition.
        CASE v_ts.value_table

            WHEN 'measurement' THEN
                SELECT MIN(h.timestamp), COUNT(*)
                INTO   v_first_ts, v_count
                FROM   intimev2.measurementhistory h
                WHERE  h.timeseries_id = v_ts.id;

                SELECT m.timestamp
                INTO   v_last_ts
                FROM   intimev2.measurement m
                WHERE  m.timeseries_id = v_ts.id;

            WHEN 'measurementstring' THEN
                SELECT MIN(h.timestamp), COUNT(*), AVG(length(h.string_value))
                INTO   v_first_ts, v_count, v_avg_size
                FROM   intimev2.measurementstringhistory h
                WHERE  h.timeseries_id = v_ts.id;

                SELECT m.timestamp
                INTO   v_last_ts
                FROM   intimev2.measurementstring m
                WHERE  m.timeseries_id = v_ts.id;

            WHEN 'measurementjson' THEN
                SELECT MIN(h.timestamp), COUNT(*), AVG(pg_column_size(h.json_value))
                INTO   v_first_ts, v_count, v_avg_size
                FROM   intimev2.measurementjsonhistory h
                WHERE  h.timeseries_id = v_ts.id;

                SELECT m.timestamp
                INTO   v_last_ts
                FROM   intimev2.measurementjson m
                WHERE  m.timeseries_id = v_ts.id;

            ELSE
                -- Unknown value_table: skip silently, will retry next cycle
                CONTINUE;

        END CASE;

        -- If history is empty but there is a current measurement, use it as first
        IF v_first_ts IS NULL THEN
            v_first_ts := v_last_ts;
        END IF;

        INSERT INTO intimev2.timeseries_stats
            (timeseries_id, first_timestamp, last_timestamp,
             record_count, avg_value_size, stats_updated_at)
        VALUES
            (v_ts.id, v_first_ts, v_last_ts,
             v_count, v_avg_size, now())
        ON CONFLICT (timeseries_id) DO UPDATE SET
            first_timestamp  = EXCLUDED.first_timestamp,
            last_timestamp   = EXCLUDED.last_timestamp,
            record_count     = EXCLUDED.record_count,
            avg_value_size   = EXCLUDED.avg_value_size,
            stats_updated_at = EXCLUDED.stats_updated_at;

        v_processed := v_processed + 1;
    END LOOP;

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
--       $$SELECT intimev2.refresh_timeseries_stats(200)$$
--   );
--
-- Useful management queries:
--   SELECT * FROM cron.job;                                  -- list jobs
--   SELECT * FROM cron.job_run_details ORDER BY start_time DESC LIMIT 20; -- history
--   SELECT cron.unschedule('refresh-timeseries-stats');      -- remove job
-- ============================================================
