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

\timing

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
\echo 'Starting migration for strings'

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
\echo 'start inserting timeseries'
SELECT NOW();
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
	

\echo 'start copying'
SELECT NOW();

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
\echo 'add constraints and indexes'
SELECT NOW();
create index concurrently idx_new_measurementstringhistory_timeseries_ts on new_measurementstringhistory (timeseries_id, timestamp);
alter table new_measurementstringhistory add CONSTRAINT fk_new_measurementstringhistory_provenance_id_provenance_pk FOREIGN KEY (provenance_id) REFERENCES intimev2.provenance(id) not valid;
alter table new_measurementstringhistory validate CONSTRAINT fk_new_measurementstringhistory_provenance_id_provenance_pk;
alter table new_measurementstringhistory add constraint fk_new_measurementstringhistory_timeseries foreign key (timeseries_id) references timeseries(id) not valid;
alter table new_measurementstringhistory validate constraint fk_new_measurementstringhistory_timeseries;
alter table new_measurementstringhistory add constraint fk_new_measurementstringhistory_partition foreign key (partition_id) references "partition"(id) not valid;
alter table new_measurementstringhistory validate constraint fk_new_measurementstringhistory_partition;

\echo 'STRING done'
SELECT NOW();

-- ##################### JSON ##############################
\echo 'Starting migration for jsons'

-- remove obsolete primary key to free up space (from the index).
alter table measurementjsonhistory drop constraint measurementjsonhistory_pkey;

-- create new history table
CREATE TABLE intimev2.new_measurementjsonhistory (
	created_on timestamp NOT NULL,
	"timestamp" timestamp NOT NULL,
	json_value jsonb NOT NULL,
	provenance_id int8 NULL,
    timeseries_id int4 not null,
    partition_id int2 not null default 1,
    json_value_md5 varchar(32) GENERATED ALWAYS AS (md5(json_value::text)) STORED NULL
);

-- fill timeseries table from both history and latest (unfortunately we have discrepancies)
\echo 'insert timeseries'
SELECT NOW();

insert into timeseries (station_id, type_id, period, value_table) 
select distinct station_id, type_id, period, 'measurementjson' from measurementjson ;

insert into timeseries (station_id, type_id, period, value_table) 
select distinct mh.station_id, mh.type_id, mh.period, 'measurementjson' 
from measurementjsonhistory mh
left outer join timeseries ts on ts.station_id = mh.station_id and ts.type_id = mh.type_id  and ts."period" = mh."period" and ts.value_table = 'measurementjson'
where ts.id is null;

-- create trigger to duplicate row and insert new timeseries records
	CREATE OR REPLACE FUNCTION intimev2.sync_measurementjsonhistory()
	RETURNS TRIGGER AS $$
	DECLARE
	    v_timeseries_id int4;
	BEGIN
	    -- Look up or insert timeseries record
	    INSERT INTO intimev2.timeseries (station_id, type_id, period, value_table)
	    VALUES (NEW.station_id, NEW.type_id, NEW.period, 'measurementjson')
	    ON CONFLICT (station_id, type_id, period, value_table) 
	    DO UPDATE SET station_id = EXCLUDED.station_id  -- dummy update to return id
	    RETURNING id INTO v_timeseries_id;
	    
	    -- Insert into new table
	    INSERT INTO intimev2.new_measurementjsonhistory (
	        created_on,
	        timestamp,
	        json_value,
	        provenance_id,
	        timeseries_id,
	        partition_id
	    ) VALUES (
	        NEW.created_on,
	        NEW.timestamp,
	        NEW.json_value,
	        NEW.provenance_id,
	        v_timeseries_id,
	        1  -- default partition_id
	    );
	    
	    RETURN NEW;
	END;
	$$ LANGUAGE plpgsql;

	CREATE TRIGGER trg_sync_measurementjsonhistory
	    AFTER INSERT ON intimev2.measurementjsonhistory
	    FOR EACH ROW
	    EXECUTE FUNCTION intimev2.sync_measurementjsonhistory();
	
\echo 'copy history'
SELECT NOW();

-- copy history to new table
DO $$
DECLARE
    v_cutoff_date timestamp;
    v_rows_affected bigint;
BEGIN
    -- Get cutoff date: min created_on from new table, or now() if empty
    SELECT COALESCE(MIN(created_on), NOW()) 
    INTO v_cutoff_date
    FROM intimev2.new_measurementjsonhistory;
    
    RAISE NOTICE 'Migrating records with created_on < %', v_cutoff_date;
    
    -- Single insert of all old records
    INSERT INTO intimev2.new_measurementjsonhistory (
        created_on,
        timestamp,
        json_value,
        provenance_id,
        timeseries_id,
        partition_id
    )
    SELECT 
        msh.created_on,
        msh.timestamp,
        msh.json_value,
        msh.provenance_id,
        ts.id,
        1
    FROM intimev2.measurementjsonhistory msh
    INNER JOIN intimev2.timeseries ts 
        ON ts.station_id = msh.station_id
        AND ts.type_id = msh.type_id
        AND ts.period = msh.period
        AND ts.value_table = 'measurementjson'
    WHERE msh.created_on < v_cutoff_date;
    
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
    
    RAISE NOTICE 'Migration complete. Total rows: %', v_rows_affected;
END $$;

-- add constraints and indexes to new table
\echo 'Adding constraints and indexes to new table'
SELECT NOW();
create index concurrently idx_new_measurementjsonhistory_timeseries_ts on new_measurementjsonhistory (timeseries_id, timestamp);
alter table new_measurementjsonhistory add CONSTRAINT fk_new_measurementjsonhistory_provenance_id_provenance_pk FOREIGN KEY (provenance_id) REFERENCES intimev2.provenance(id) not valid;
alter table new_measurementjsonhistory validate CONSTRAINT fk_new_measurementjsonhistory_provenance_id_provenance_pk;
alter table new_measurementjsonhistory add constraint fk_new_measurementjsonhistory_timeseries foreign key (timeseries_id) references timeseries(id) not valid;
alter table new_measurementjsonhistory validate constraint fk_new_measurementjsonhistory_timeseries;
alter table new_measurementjsonhistory add constraint fk_new_measurementjsonhistory_partition foreign key (partition_id) references "partition"(id) not valid;
alter table new_measurementjsonhistory validate constraint fk_new_measurementjsonhistory_partition;
\echo 'done JSON'
SELECT NOW();


-- ##################### DOUBLE ##############################
\echo 'Starting migration for doubles'

-- remove obsolete primary key to free up space (from the index).
alter table measurementhistory drop constraint measurementhistory_pkey;

-- create new history table
CREATE TABLE intimev2.new_measurementhistory (
	created_on timestamp NOT NULL,
	"timestamp" timestamp NOT NULL,
	double_value float8 NOT NULL,
	provenance_id int8 NULL,
    timeseries_id int4 not null,
    partition_id int2 not null default 1
);

-- fill timeseries table from both history and latest (unfortunately we have discrepancies)
\echo 'insert timeseries'
SELECT NOW();

insert into timeseries (station_id, type_id, period, value_table) 
select distinct station_id, type_id, period, 'measurement' from measurement ;

insert into timeseries (station_id, type_id, period, value_table) 
select distinct mh.station_id, mh.type_id, mh.period, 'measurement' 
from measurementhistory mh
left outer join timeseries ts on ts.station_id = mh.station_id and ts.type_id = mh.type_id  and ts."period" = mh."period" and ts.value_table = 'measurement'
where ts.id is null;

-- create trigger to duplicate row and insert new timeseries records
	CREATE OR REPLACE FUNCTION intimev2.sync_measurementhistory()
	RETURNS TRIGGER AS $$
	DECLARE
	    v_timeseries_id int4;
	BEGIN
	    -- Look up or insert timeseries record
	    INSERT INTO intimev2.timeseries (station_id, type_id, period, value_table)
	    VALUES (NEW.station_id, NEW.type_id, NEW.period, 'measurement')
	    ON CONFLICT (station_id, type_id, period, value_table) 
	    DO UPDATE SET station_id = EXCLUDED.station_id  -- dummy update to return id
	    RETURNING id INTO v_timeseries_id;
	    
	    -- Insert into new table
	    INSERT INTO intimev2.new_measurementhistory (
	        created_on,
	        timestamp,
	        double_value,
	        provenance_id,
	        timeseries_id,
	        partition_id
	    ) VALUES (
	        NEW.created_on,
	        NEW.timestamp,
	        NEW.double_value,
	        NEW.provenance_id,
	        v_timeseries_id,
	        1  -- default partition_id
	    );
	    
	    RETURN NEW;
	END;
	$$ LANGUAGE plpgsql;

	CREATE TRIGGER trg_sync_measurementhistory
	    AFTER INSERT ON intimev2.measurementhistory
	    FOR EACH ROW
	    EXECUTE FUNCTION intimev2.sync_measurementhistory();
	
\echo 'copy history'
SELECT NOW();

-- copy history to new table
DO $$
DECLARE
    v_cutoff_date timestamp;
    v_rows_affected bigint;
BEGIN
    -- Get cutoff date: min created_on from new table, or now() if empty
    SELECT COALESCE(MIN(created_on), NOW()) 
    INTO v_cutoff_date
    FROM intimev2.new_measurementhistory;
    
    RAISE NOTICE 'Migrating records with created_on < %', v_cutoff_date;
    
    -- Single insert of all old records
    INSERT INTO intimev2.new_measurementhistory (
        created_on,
        timestamp,
        double_value,
        provenance_id,
        timeseries_id,
        partition_id
    )
    SELECT 
        msh.created_on,
        msh.timestamp,
        msh.double_value,
        msh.provenance_id,
        ts.id,
        1
    FROM intimev2.measurementhistory msh
    INNER JOIN intimev2.timeseries ts 
        ON ts.station_id = msh.station_id
        AND ts.type_id = msh.type_id
        AND ts.period = msh.period
        AND ts.value_table = 'measurement'
    WHERE msh.created_on < v_cutoff_date;
    
    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
    
    RAISE NOTICE 'Migration complete. Total rows: %', v_rows_affected;
END $$;

\echo 'Adding constraints and indexes to new table'
SELECT NOW();
create index concurrently idx_new_measurementhistory_timeseries_ts on new_measurementhistory (timeseries_id, timestamp);
alter table new_measurementhistory add CONSTRAINT fk_new_measurementhistory_provenance_id_provenance_pk FOREIGN KEY (provenance_id) REFERENCES intimev2.provenance(id) not valid;
alter table new_measurementhistory validate CONSTRAINT fk_new_measurementhistory_provenance_id_provenance_pk;
alter table new_measurementhistory add constraint fk_new_measurementhistory_timeseries foreign key (timeseries_id) references timeseries(id) not valid;
alter table new_measurementhistory validate constraint fk_new_measurementhistory_timeseries;
alter table new_measurementhistory add constraint fk_new_measurementhistory_partition foreign key (partition_id) references "partition"(id) not valid;
alter table new_measurementhistory validate constraint fk_new_measurementhistory_partition;
\echo 'done JSON'
SELECT NOW();
