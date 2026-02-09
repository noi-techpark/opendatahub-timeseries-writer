-- SPDX-FileCopyrightText: 2026 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0

set search_path=intimev2;
set work_mem = '20MB';

DO $$
DECLARE
  -- Define your partitions here
  v_partitions CONSTANT JSONB := '[
  {"name": "a22-traffic", "origin": "A22", "station_types": ["TrafficSensor", "TrafficDirection"]},
  {"name": "a22-environment", "origin": "a22-algorab", "station_types": null},
  {"name": "traffic", "origin": null, "station_types": ["TrafficSensor", "TrafficDirection"]},
  {"name": "parking", "origin": null, "station_types": ["ParkingStation", "ParkingSensor", "ParkingFacility"]},
  {"name": "sharedmobility", "origin": "sharedmobility-ch", "station_types": null},
  {"name": "alpsgo", "origin": "AlpsGo", "station_types": null},
  {"name": "ummadum", "origin": "UMMADUM", "station_types": null},
  {"name": "echarging", "origin": null, "station_types": ["EChargingStation", "EChargingPlug"]},
  {"name": "meteo", "origin": null, "station_types": ["MeteoStation", "WeatherForecast"]}
  ]'::JSONB;

  v_partition JSONB;
  v_partition_id INT;
  v_partition_name TEXT;
  v_origin TEXT;
  v_station_types TEXT[];
  v_station_type TEXT;
  v_ts_record RECORD;
  v_total_timeseries INT;
BEGIN
  -- Loop through each partition definition
  FOR v_partition IN SELECT * FROM jsonb_array_elements(v_partitions)
  LOOP
    v_partition_name := v_partition->>'name';
    v_origin := v_partition->>'origin';
    v_station_types := CASE
      WHEN v_partition->>'station_types' IS NOT NULL
           AND jsonb_array_length(v_partition->'station_types') > 0
      THEN ARRAY(SELECT jsonb_array_elements_text(v_partition->'station_types'))
      ELSE NULL
  END;

  v_total_timeseries := 0;

  RAISE NOTICE '=== Processing partition: % ===', v_partition_name;

    -- Create partition entry and get ID
  INSERT INTO "partition" (name, description)
  VALUES (v_partition_name, v_partition_name)
  ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
  RETURNING id INTO v_partition_id;

  RAISE NOTICE 'Created partition % with ID %', v_partition_name, v_partition_id;

  -- Create partition definitions
  IF v_station_types IS NULL AND v_origin IS NOT NULL THEN
    INSERT INTO partition_def(partition_id, origin, stationtype)
    VALUES (v_partition_id, v_origin, NULL)
  on conflict do nothing;

  ELSIF v_origin IS NULL AND v_station_types IS NOT NULL THEN
    FOREACH v_station_type IN ARRAY v_station_types
    LOOP
      INSERT INTO partition_def(partition_id, origin, stationtype)
      VALUES (v_partition_id, NULL, v_station_type)
    on conflict do nothing;
    END LOOP;

  ELSIF v_origin IS NOT NULL AND v_station_types IS NOT NULL THEN
    FOREACH v_station_type IN ARRAY v_station_types
    LOOP
      INSERT INTO partition_def(partition_id, origin, stationtype)
      VALUES (v_partition_id, v_origin, v_station_type)
      on conflict do nothing;
    END LOOP;
  ELSE
    INSERT INTO partition_def(partition_id, origin, stationtype)
    VALUES (v_partition_id, NULL, NULL)
    on conflict do nothing;
  END IF;

  -- Create partition tables
  EXECUTE format('CREATE TABLE IF NOT EXISTS measurementhistory_%s PARTITION OF measurementhistory FOR VALUES IN (%s)',
                 v_partition_id, v_partition_id);
  EXECUTE format('CREATE TABLE IF NOT EXISTS measurementstringhistory_%s PARTITION OF measurementstringhistory FOR VALUES IN (%s)',
                 v_partition_id, v_partition_id);
  EXECUTE format('CREATE TABLE IF NOT EXISTS measurementjsonhistory_%s PARTITION OF measurementjsonhistory FOR VALUES IN (%s)',
                 v_partition_id, v_partition_id);

  -- Update timeseries
  UPDATE timeseries ts
  SET partition_id = v_partition_id
  FROM station s
  WHERE s.id = ts.station_id
    AND (v_origin IS NULL OR s.origin = v_origin)
    AND (v_station_types IS NULL OR s.stationtype = ANY(v_station_types))
    AND ts.partition_id != v_partition_id;

  RAISE NOTICE 'Updated timeseries partition_id';

  -- Migrate history data
  FOR v_ts_record IN
    SELECT id, value_table, partition_id
    FROM timeseries
    WHERE partition_id = v_partition_id
  LOOP
    v_total_timeseries := v_total_timeseries + 1;

    CASE v_ts_record.value_table
      WHEN 'measurementstring' THEN
        UPDATE measurementstringhistory h
        SET partition_id = v_ts_record.partition_id
        WHERE h.timeseries_id = v_ts_record.id
          AND h.partition_id != v_ts_record.partition_id;

      WHEN 'measurementjson' THEN
        UPDATE measurementjsonhistory h
        SET partition_id = v_ts_record.partition_id
        WHERE h.timeseries_id = v_ts_record.id
          AND h.partition_id != v_ts_record.partition_id;

      WHEN 'measurement' THEN
        UPDATE measurementhistory h
        SET partition_id = v_ts_record.partition_id
        WHERE h.timeseries_id = v_ts_record.id
          AND h.partition_id != v_ts_record.partition_id;
    END CASE;

    IF v_total_timeseries % 100 = 0 THEN
      RAISE NOTICE 'Processed % timeseries', v_total_timeseries;
    END IF;
  END LOOP;

  COMMIT;
    RAISE NOTICE 'Completed partition %: Processed % timeseries', v_partition_name, v_total_timeseries;
  END LOOP;

  RAISE NOTICE '=== All partitions completed ===';
END $$;