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
  v_total_timeseries INT;
BEGIN
  FOR v_partition_id, v_partition_name IN
    SELECT id, name FROM "partition" ORDER BY id
  LOOP
    v_total_timeseries := 0;

    RAISE NOTICE '=== Processing partition: % (id=%) ===', v_partition_name, v_partition_id;
    RAISE NOTICE 'TIME: %', now();

    FOR v_ts_record IN
      SELECT ts.id, ts.type_id, ts.period, ts.value_table
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
    LOOP
      UPDATE timeseries ts
        SET partition_id = v_partition_id
        WHERE ts.id = v_ts_record.id;

      COMMIT;

      CASE v_ts_record.value_table
        WHEN 'measurementstring' THEN
          UPDATE measurementstringhistory h
          SET partition_id = v_partition_id
          WHERE h.timeseries_id = v_ts_record.id
            AND h.partition_id != v_partition_id;

        WHEN 'measurementjson' THEN
          UPDATE measurementjsonhistory h
          SET partition_id = v_partition_id
          WHERE h.timeseries_id = v_ts_record.id
            AND h.partition_id != v_partition_id;

        WHEN 'measurement' THEN
          UPDATE measurementhistory h
          SET partition_id = v_partition_id
          WHERE h.timeseries_id = v_ts_record.id
            AND h.partition_id != v_partition_id;
      END CASE;

      COMMIT;

      v_total_timeseries := v_total_timeseries + 1;
      IF v_total_timeseries % 100 = 0 THEN
        RAISE NOTICE 'Processed % timeseries: %', v_total_timeseries, now();
      END IF;
    END LOOP;

    RAISE NOTICE 'Completed partition %: Processed % timeseries at %', v_partition_name, v_total_timeseries, now();
  END LOOP;

  RAISE NOTICE '=== Cleaning up orphaned records ===';
  RAISE NOTICE 'TIME: %', now();

  PERFORM pg_sleep(30);

  UPDATE measurementhistory h
    SET partition_id = t.partition_id
    FROM timeseries t
    WHERE t.id = h.timeseries_id
      AND t.partition_id <> h.partition_id;
  UPDATE measurementstringhistory h
    SET partition_id = t.partition_id
    FROM timeseries t
    WHERE t.id = h.timeseries_id
      AND t.partition_id <> h.partition_id;
  UPDATE measurementjsonhistory h
    SET partition_id = t.partition_id
    FROM timeseries t
    WHERE t.id = h.timeseries_id
      AND t.partition_id <> h.partition_id;

  RAISE NOTICE '=== All partitions completed ===';
  RAISE NOTICE 'TIME: %', now();

END $$;
