-- SPDX-FileCopyrightText: 2025 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0

-- set session parameters
set search_path=intimev2,public;
SET work_mem = '2GB';
SET maintenance_work_mem = '40GB';
SET max_parallel_workers_per_gather = 8;
SET max_parallel_maintenance_workers = 8;
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


-- ##################### STRING ##############################
RAISE NOTICE 'Starting migration for strings: %', CURRENT_DATE;

-- remove obsolete primary key to free up space (from the index).
alter table measurementstringhistory drop constraint measurementstringhistory_pkey;

-- create new history table
CREATE TABLE intimev2.new_measurementstringhistory (
	created_on timestamp NOT NULL,
	"timestamp" timestamp NOT NULL,
	string_value text NOT NULL,
	provenance_id int8 NULL,
    timeseries_id int4 not null,
    partition_id int2 not null default 1
);

-- fill timeseries table from both history and latest (unfortunately we have discrepancies)
RAISE NOTICE 'Start inserting timeseries: %', CURRENT_DATE;

insert into timeseries (station_id, type_id, period, value_table) 
select distinct station_id, type_id, period, 'measurementstring' from measurementstring ;

insert into timeseries (station_id, type_id, period, value_table) 
select distinct mh.station_id, mh.type_id, mh.period, 'measurementstring' 
from measurementstringhistory mh
left outer join timeseries ts on ts.station_id = mh.station_id and ts.type_id = mh.type_id  and ts."period" = mh."period" and ts.value_table = 'measurementstring'
where ts.id is null;

-- create trigger to duplicate row and insert new timeseries records
	CREATE OR REPLACE FUNCTION intimev2.sync_measurementstringhistory()
	RETURNS TRIGGER AS $$
	DECLARE
	    v_timeseries_id int4;
	BEGIN
	    -- Look up or insert timeseries record
	    INSERT INTO intimev2.timeseries (station_id, type_id, period, value_table)
	    VALUES (NEW.station_id, NEW.type_id, NEW.period, 'measurementstring')
	    ON CONFLICT (station_id, type_id, period, value_table) 
	    DO UPDATE SET station_id = EXCLUDED.station_id  -- dummy update to return id
	    RETURNING id INTO v_timeseries_id;
	    
	    -- Insert into new table
	    INSERT INTO intimev2.new_measurementstringhistory (
	        created_on,
	        timestamp,
	        string_value,
	        provenance_id,
	        timeseries_id,
	        partition_id
	    ) VALUES (
	        NEW.created_on,
	        NEW.timestamp,
	        NEW.string_value,
	        NEW.provenance_id,
	        v_timeseries_id,
	        1  -- default partition_id
	    );
	    
	    RETURN NEW;
	END;
	$$ LANGUAGE plpgsql;

	CREATE TRIGGER trg_sync_measurementstringhistory
	    AFTER INSERT ON intimev2.measurementstringhistory
	    FOR EACH ROW
	    EXECUTE FUNCTION intimev2.sync_measurementstringhistory();
	
RAISE NOTICE 'Start copying history: %', CURRENT_DATE;

-- copy history to new table
DO $$
DECLARE
    v_cutoff_date timestamp;
    v_rows_affected bigint;
BEGIN
    -- Get cutoff date: min created_on from new table, or now() if empty
    SELECT COALESCE(MIN(created_on), NOW()) 
    INTO v_cutoff_date
    FROM intimev2.new_measurementstringhistory;
    
    RAISE NOTICE 'Migrating records with created_on < %', v_cutoff_date;
    
    -- Single insert of all old records
    INSERT INTO intimev2.new_measurementstringhistory (
        created_on,
        timestamp,
        string_value,
        provenance_id,
        timeseries_id,
        partition_id
    )
    SELECT 
        msh.created_on,
        msh.timestamp,
        msh.string_value,
        msh.provenance_id,
        ts.id,
        1
    FROM intimev2.measurementstringhistory msh
    INNER JOIN intimev2.timeseries ts 
        ON ts.station_id = msh.station_id
        AND ts.type_id = msh.type_id
        AND ts.period = msh.period
        AND ts.value_table = 'measurementstring'
    WHERE msh.created_on < v_cutoff_date;
    
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
    
    RAISE NOTICE 'Migration complete. Total rows: %', v_rows_affected;
END $$;

-- add constraints and indexes to new table
RAISE NOTICE 'Adding constraints and indexes to new table: %', CURRENT_DATE;
alter table new_measurementstringhistory add CONSTRAINT fk_new_measurementstringhistory_provenance_id_provenance_pk FOREIGN KEY (provenance_id) REFERENCES intimev2.provenance(id),
alter table new_measurementstringhistory add constraint fk_new_measurementstringhistory_timeseries foreign key (timeseries_id) references timeseries(id),
alter table new_measurementstringhistory add constraint fk_new_measurementstringhistory_partition foreign key (partition_id) references "partition"(id)
-- -- TODO: make this unique, but we have conflicts
create index idx_new_measurementstringhistory_timeseries_ts on new_measurementstringhistory (timeseries_id, timestamp);


-- JSON
alter table measurementjson add column timeseries_id int4;
alter table measurementjson add constraint fk_measurementjson_timeseries foreign key (timeseries_id) references timeseries(id);

insert into timeseries (station_id, type_id, period, value_table) 
select station_id, type_id, period, 'measurementjson' from measurementjson
union select distinct station_id, type_id, period, 'measurementjson' from measurementjsonhistory ;

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
create index idx_measurementjsonhistory_timeseries_ts on measurementjsonhistory (timeseries_id, timestamp);
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

insert into timeseries (station_id, type_id, period, value_table) 
select station_id, type_id, period, 'measurement' from measurement
union select distinct station_id, type_id, period, 'measurement' from measurementhistory ;

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

drop index if exists idx_ts_lookup;
CREATE INDEX CONCURRENTLY idx_ts_lookup 
ON timeseries(station_id, type_id, period, value_table) 
INCLUDE (id);

DO $$
DECLARE
    batch_size INT := 100000;
    start_page INT := 0;
    max_page INT := 70000000;
BEGIN
    SELECT relpages INTO max_page 
    FROM pg_class 
    WHERE relname = 'measurementhistory';

    RAISE NOTICE 'Total pages: %', max_page; 

    WHILE start_page < max_page LOOP
        UPDATE measurementhistory h
        SET timeseries_id = t.id, partition_id = 1
        FROM timeseries t
        WHERE t.station_id = h.station_id
        AND t.type_id = h.type_id
        AND t.period = h."period"
        AND t.value_table = 'measurement'
        AND h.timeseries_id IS NULL
        AND h.ctid >= ('(' || start_page || ',0)')::tid 
        AND h.ctid < ('(' || (start_page + batch_size) || ',0)')::tid;
        
        RAISE NOTICE 'Completed batch starting at page %', start_page;
        start_page := start_page + batch_size; 
        commit;
    END LOOP;
END $$; 

update measurementhistory h
set timeseries_id = t.id, partition_id = 1
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurement'
and h.timeseries_id is null;

drop index idx_ts_lookup;

create unique index idx_measurement_timeseries_ts on measurement (timeseries_id);
alter table measurement alter column timeseries_id set not null;
create index idx_measurementhistory_timeseries_ts on measurementhistory (timeseries_id, timestamp);
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

-- SHUTDOWN BDP SERVICE HERE

-- UPDATE LATEST TABLES
alter table measurementstring add column timeseries_id int4;
alter table measurementstring add constraint fk_measurementstring_timeseries foreign key (timeseries_id) references timeseries(id);
create unique index idx_measurementstring_timeseries_ts on measurementstring (timeseries_id);
update measurementstring h
set timeseries_id = t.id
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurementstring';
alter table measurementstring alter column timeseries_id set not null;


drop table old_measurementstring;
drop table old_measurementstringhistory;

drop function sync_measurementstringhistory;

--cluster measurementhistory using idx_measurementhistory_timeseries_ts;
