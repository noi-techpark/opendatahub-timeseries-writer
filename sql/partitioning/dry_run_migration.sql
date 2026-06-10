-- SPDX-FileCopyrightText: 2026 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0

set search_path=intimev2;
set work_mem = '20MB';

DO $$
DECLARE
  v_partition_id INT;
  v_partition_name TEXT;
  v_ts_record RECORD;
BEGIN
  RAISE NOTICE '=== The following migrations will be done ===';
  FOR v_partition_id, v_partition_name IN
    SELECT id, name FROM "partition" ORDER BY id
  LOOP
    FOR v_ts_record IN
      SELECT
        COALESCE(p_old.name, sub.current_partition_id::TEXT) AS old_partition,
        sub.origin,
        COUNT(*) AS cnt
      FROM (
        SELECT ts.partition_id AS current_partition_id, s.origin
        FROM timeseries ts
        JOIN station s ON s.id = ts.station_id
        WHERE ts.partition_id != v_partition_id
          AND v_partition_id = (
            WITH scored AS (
              SELECT pd.partition_id,
                     (CASE WHEN pd.origin = s.origin THEN 1 ELSE 0 END +
                      CASE WHEN pd.stationtype = s.stationtype THEN 1 ELSE 0 END +
                      CASE WHEN pd.type_id = ts.type_id THEN 1 ELSE 0 END +
                      CASE WHEN pd.period = ts.period THEN 1 ELSE 0 END) AS score
              FROM partition_def pd
              WHERE (pd.origin IS NULL OR pd.origin = s.origin)
                AND (pd.stationtype IS NULL OR pd.stationtype = s.stationtype)
                AND (pd.type_id IS NULL OR pd.type_id = ts.type_id)
                AND (pd.period IS NULL OR pd.period = ts.period)
            )
            SELECT partition_id FROM scored
            WHERE score = (SELECT MAX(score) FROM scored)
            ORDER BY partition_id DESC
            LIMIT 1
          )
      ) sub
      LEFT JOIN "partition" p_old ON p_old.id = sub.current_partition_id
      GROUP BY sub.current_partition_id, p_old.name, sub.origin
    LOOP
      RAISE NOTICE '% [origin: %] -> % (%)', v_ts_record.old_partition, v_ts_record.origin, v_partition_name, v_ts_record.cnt;
    END LOOP;

  END LOOP;

  RAISE NOTICE '=== Dry run complete ===';
END $$;
