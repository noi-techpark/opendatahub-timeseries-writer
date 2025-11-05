-- SPDX-FileCopyrightText: 2025 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0

-- set session parameters
set search_path=intimev2,public;
SET work_mem = '10GB';
SET maintenance_work_mem = '20GB';
SET max_parallel_workers_per_gather = 4;
SET synchronous_commit = OFF;

-- create new tables
create sequence partition_seq start 1 increment 1;
create table "partition" (
	id  int2 default nextval('partition_seq') not null,
	name varchar(60) not null,
	description text,
	primary key (id),
	constraint uc_tpartition_name unique (name)
);
insert into "partition" (name, description) values ('default', 'Default partition');

create sequence timeseries_seq start 1 increment 1;
create table  timeseries (
	id  int4 default nextval('timeseries_seq') not null,
	station_id int8 not null,
	type_id int8 not null,
	period int4 not null,
	value_table varchar(60) not null,
	partition_id int2 not null default 1,
	primary key (id),
	constraint uc_timeseries_station_id_type_id_period unique (station_id, type_id, period, value_table),
	constraint fk_timeseries_partition foreign key (partition_id) references "partition"(id),
	constraint fk_timeseries_station foreign key (station_id) references "station"(id),
	constraint fk_timeseries_type foreign key (type_id) references "type"(id)
);
create index idx_timeseries_station on timeseries(station_id);
create index idx_timeseries_type on timeseries(type_id);

grant select on timeseries, partition to bdp_readonly;

insert into timeseries (station_id, type_id, period, value_table) 
select station_id, type_id, period, 'measurement' from measurement
union select distinct station_id, type_id, period, 'measurement' from measurementhistory ;

insert into timeseries (station_id, type_id, period, value_table) 
select station_id, type_id, period, 'measurementstring' from measurementstring
union select distinct station_id, type_id, period, 'measurementstring' from measurementstringhistory ;

insert into timeseries (station_id, type_id, period, value_table) 
select station_id, type_id, period, 'measurementjson' from measurementjson
union select distinct station_id, type_id, period, 'measurementjson' from measurementjsonhistory ;

-- STRING
alter table measurementstring add column timeseries_id int4;
alter table measurementstring add constraint fk_measurementstring_timeseries foreign key (timeseries_id) references timeseries(id);
alter table measurementstringhistory add column timeseries_id int4;
alter table measurementstringhistory add column partition_id int2 not null default 1;
alter table measurementstringhistory add constraint fk_measurementstringhistory_timeseries foreign key (timeseries_id) references timeseries(id);
alter table measurementstringhistory add constraint fk_measurementstringhistory_partition foreign key (partition_id) references "partition"(id);

update measurementstring h
set timeseries_id = t.id
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurementstring';

alter table measurementstringhistory drop constraint measurementstringhistory_pkey;
drop index measurementstringhistory_pkey; 

DO $$
DECLARE
	rows_affected INTEGER;
	total_updated INTEGER := 0;
	iteration INTEGER := 1;
	batch_size INTEGER := 1000000;
	last_ctid tid;
	max_ctid tid;
BEGIN
	-- Get the maximum ctid to know when to stop
	SELECT max(ctid) INTO max_ctid FROM measurementstringhistory;
	last_ctid := '(0,0)'::tid;
	
	RAISE NOTICE 'Starting batch update process...';
	RAISE NOTICE 'Batch size: %', batch_size;
	
	LOOP
		RAISE NOTICE '----------------------------------------';
		RAISE NOTICE 'Iteration % - %', iteration, clock_timestamp();
		
		-- Update next batch based on ctid range
		WITH updated AS (
			UPDATE measurementstringhistory h
			SET timeseries_id = t.id, partition_id = 1
			FROM timeseries t
			WHERE t.station_id = h.station_id
			  AND t.type_id = h.type_id
			  AND t.period = h.period
			  AND t.value_table = 'measurementstring'
			  AND h.timeseries_id IS NULL
			  AND h.ctid > last_ctid
			  AND h.ctid <= (
				SELECT ctid FROM measurementstringhistory 
				WHERE ctid > last_ctid 
				ORDER BY ctid 
				LIMIT 1 OFFSET batch_size
			  )
			RETURNING h.ctid
		)
		SELECT COUNT(*), MAX(ctid) INTO rows_affected, last_ctid FROM updated;
		
		total_updated := total_updated + rows_affected;
		
		RAISE NOTICE 'Rows processed in range, updated: %', rows_affected;
		RAISE NOTICE 'Total updated so far: %', total_updated;
		RAISE NOTICE 'Last ctid: %', last_ctid;
		
		-- Exit if we've passed the max ctid or no progress
		EXIT WHEN last_ctid >= max_ctid OR last_ctid IS NULL;
		
		iteration := iteration + 1;
		COMMIT;
		
	END LOOP;
	
	RAISE NOTICE '----------------------------------------';
	RAISE NOTICE 'Update complete! Total rows updated: %', total_updated;
END $$;

update measurementstringhistory h
set timeseries_id = t.id, partition_id = 1
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurementstring'
and h.timeseries_id is null;

create unique index idx_measurementstring_timeseries_ts on measurementstring (timeseries_id);
alter table measurementstring alter column timeseries_id set not null;
create unique index idx_measurementstringhistory_timeseries_ts on measurementstringhistory (timeseries_id, timestamp);
alter table measurementstringhistory alter column timeseries_id set not null;

alter table measurementstring drop column id;
alter table measurementstring drop column station_id;
alter table measurementstring drop column type_id;
alter table measurementstring drop column period;
alter table measurementstringhistory drop column id;
alter table measurementstringhistory drop column station_id;
alter table measurementstringhistory drop column type_id;
alter table measurementstringhistory drop column period;

vacuum full measurementstring;
vacuum full measurementstringhistory;

-- JSON
alter table measurementjson add column timeseries_id int4;
alter table measurementjson add constraint fk_measurementjson_timeseries foreign key (timeseries_id) references timeseries(id);
alter table measurementjsonhistory add column timeseries_id int4;
alter table measurementjsonhistory add column partition_id int2 not null default 1;
alter table measurementjsonhistory add constraint fk_measurementjsonhistory_timeseries foreign key (timeseries_id) references timeseries(id);
alter table measurementjsonhistory add constraint fk_measurementjsonhistory_partition foreign key (partition_id) references "partition"(id);

update measurementjson h
set timeseries_id = t.id
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurementjson';

update measurementjsonhistory h
set timeseries_id = t.id, partition_id = 1
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurementjson';

create unique index idx_measurementjson_timeseries_ts on measurementjson (timeseries_id);
alter table measurementjson alter column timeseries_id set not null;
create unique index idx_measurementjsonhistory_timeseries_ts on measurementjsonhistory (timeseries_id, timestamp);
alter table measurementjsonhistory alter column timeseries_id set not null;

alter table measurementjson drop column id;
alter table measurementjson drop column station_id;
alter table measurementjson drop column type_id;
alter table measurementjson drop column period;
alter table measurementjsonhistory drop column id;
alter table measurementjsonhistory drop column station_id;
alter table measurementjsonhistory drop column type_id;
alter table measurementjsonhistory drop column period;

vacuum full measurementjson;
vacuum full measurementjsonhistory;

-- DOUBLE
alter table measurement add column timeseries_id int4;
alter table measurement add constraint fk_measurement_timeseries foreign key (timeseries_id) references timeseries(id);
alter table measurementhistory add column timeseries_id int4;
alter table measurementhistory add column partition_id int2 not null default 1;
alter table measurementhistory add constraint fk_measurementhistory_timeseries foreign key (timeseries_id) references timeseries(id);
alter table measurementhistory add constraint fk_measurementhistory_partition foreign key (partition_id) references "partition"(id);

update measurement h
set timeseries_id = t.id
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurement';

alter table measurementhistory drop constraint measurementhistory_pkey;
drop index measurementhistory_pkey; 

DO $$
DECLARE
    rows_affected INTEGER;
    total_updated INTEGER := 0;
    iteration INTEGER := 1;
    batch_size INTEGER := 1000000;
    last_ctid tid;
    max_ctid tid;
BEGIN
    -- Get the maximum ctid to know when to stop
    SELECT max(ctid) INTO max_ctid FROM measurementhistory;
    last_ctid := '(0,0)'::tid;
    
    RAISE NOTICE 'Starting batch update process...';
    RAISE NOTICE 'Batch size: %', batch_size;
    
    LOOP
        RAISE NOTICE '----------------------------------------';
        RAISE NOTICE 'Iteration % - %', iteration, clock_timestamp();
        
        -- Update next batch based on ctid range
        WITH updated AS (
            UPDATE measurementhistory h
            SET timeseries_id = t.id, partition_id = 1
            FROM timeseries t
            WHERE t.station_id = h.station_id
              AND t.type_id = h.type_id
              AND t.period = h.period
              AND t.value_table = 'measurement'
              AND h.timeseries_id IS NULL
              AND h.ctid > last_ctid
              AND h.ctid <= (
                SELECT ctid FROM measurementhistory 
                WHERE ctid > last_ctid 
                ORDER BY ctid 
                LIMIT 1 OFFSET batch_size
              )
            RETURNING h.ctid
        )
        SELECT COUNT(*), MAX(ctid) INTO rows_affected, last_ctid FROM updated;
        
        total_updated := total_updated + rows_affected;
        
        RAISE NOTICE 'Rows processed in range, updated: %', rows_affected;
        RAISE NOTICE 'Total updated so far: %', total_updated;
        RAISE NOTICE 'Last ctid: %', last_ctid;
        
        -- Exit if we've passed the max ctid or no progress
        EXIT WHEN last_ctid >= max_ctid OR last_ctid IS NULL;
        
        iteration := iteration + 1;
        COMMIT;
        
    END LOOP;
    
    RAISE NOTICE '----------------------------------------';
    RAISE NOTICE 'Update complete! Total rows updated: %', total_updated;
END $$;

update measurementhistory h
set timeseries_id = t.id, partition_id = 1
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurement'
and h.timeseries_id is null;

create unique index idx_measurement_timeseries_ts on measurement (timeseries_id);
alter table measurement alter column timeseries_id set not null;
create unique index idx_measurementhistory_timeseries_ts on measurementhistory (timeseries_id, timestamp);
alter table measurementhistory alter column timeseries_id set not null;

alter table measurement drop column id;
alter table measurement drop column station_id;
alter table measurement drop column type_id;
alter table measurement drop column period;
alter table measurementhistory drop column id;
alter table measurementhistory drop column station_id;
alter table measurementhistory drop column type_id;
alter table measurementhistory drop column period;

vacuum full measurement;
vacuum full measurementhistory;

vacuum full station;

--cluster measurementhistory using idx_measurementhistory_timeseries_ts;