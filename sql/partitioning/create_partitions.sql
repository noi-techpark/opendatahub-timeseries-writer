-- SPDX-FileCopyrightText: 2026 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0

set search_path=intimev2;
set work_mem = '20MB';

DO $$
DECLARE
  v_partitions CONSTANT JSONB := '[
  {"name": "a22-traffic", "origin": "A22", "station_types": ["TrafficSensor", "TrafficDirection"]},
  {"name": "a22-environment", "origin": "a22-algorab", "station_types": null},
  {"name": "traffic", "origin": null, "station_types": ["TrafficSensor", "TrafficDirection"]},
  {"name": "parking", "origin": null, "station_types": ["ParkingStation", "ParkingSensor", "ParkingFacility"]},
  {"name": "parking-ch", "origin": "SBB", "station_types": ["ParkingStation", "BikeParking"]},
  {"name": "sharedmobility", "origin": "sharedmobility-ch", "station_types": null},
  {"name": "emobility-ch", "origin": "BFE", "station_types": ["EChargingStation","EChargingPlug"]},
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
BEGIN
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

    RAISE NOTICE '=== Processing partition: % ===', v_partition_name;

    INSERT INTO "partition" (name, description)
    VALUES (v_partition_name, v_partition_name)
    ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
    RETURNING id INTO v_partition_id;

    IF v_station_types IS NULL AND v_origin IS NOT NULL THEN
      INSERT INTO partition_def(partition_id, origin, stationtype)
      VALUES (v_partition_id, v_origin, NULL)
      ON CONFLICT DO NOTHING;
    ELSIF v_origin IS NULL AND v_station_types IS NOT NULL THEN
      FOREACH v_station_type IN ARRAY v_station_types
      LOOP
        INSERT INTO partition_def(partition_id, origin, stationtype)
        VALUES (v_partition_id, NULL, v_station_type)
        ON CONFLICT DO NOTHING;
      END LOOP;
    ELSIF v_origin IS NOT NULL AND v_station_types IS NOT NULL THEN
      FOREACH v_station_type IN ARRAY v_station_types
      LOOP
        INSERT INTO partition_def(partition_id, origin, stationtype)
        VALUES (v_partition_id, v_origin, v_station_type)
        ON CONFLICT DO NOTHING;
      END LOOP;
    ELSE
      INSERT INTO partition_def(partition_id, origin, stationtype)
      VALUES (v_partition_id, NULL, NULL)
      ON CONFLICT DO NOTHING;
    END IF;

    EXECUTE format('CREATE TABLE IF NOT EXISTS measurementhistory_%s PARTITION OF measurementhistory FOR VALUES IN (%s)',
                   v_partition_id, v_partition_id);
    EXECUTE format('CREATE TABLE IF NOT EXISTS measurementstringhistory_%s PARTITION OF measurementstringhistory FOR VALUES IN (%s)',
                   v_partition_id, v_partition_id);
    EXECUTE format('CREATE TABLE IF NOT EXISTS measurementjsonhistory_%s PARTITION OF measurementjsonhistory FOR VALUES IN (%s)',
                   v_partition_id, v_partition_id);

    COMMIT;
    RAISE NOTICE 'Created partition % with ID %', v_partition_name, v_partition_id;
  END LOOP;

  RAISE NOTICE '=== All partitions created ===';
END $$;
