
-- SPDX-FileCopyrightText: 2025 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0
set search_path=intimev2,public;

-- set session parameters
SET work_mem = '2GB';
SET maintenance_work_mem = '40GB';
SET max_parallel_workers_per_gather = 8;
SET max_parallel_maintenance_workers = 8;

-- create check constraint corresponding to the partition key, so that during attachment now (exclusive lock) validation has to be done
ALTER TABLE measurementhistory 
    ADD CONSTRAINT ck_measurementhistory_part_1 
    CHECK (partition_id = 1)
    not valid;
-- create and validate separately to avoid exclusive locks
alter table measurementhistory validate constraint ck_measurementhistory_part_1;

--drop table part_measurementhistory;
-- create partition main table part_measurementhistory as exact clone of measurementhistory
-- pg_dump -U bdp -W -h partition.czracduepxal.eu-west-1.rds.amazonaws.com -d bdp -t intimev2.measurementhistory --schema-only
CREATE TABLE part_measurementhistory (
	created_on timestamp NOT NULL,
	"timestamp" timestamp NOT NULL,
	double_value float8 NOT NULL,
	provenance_id int8 NULL,
	timeseries_id int4 NULL,
	partition_id int2 DEFAULT 1 NOT NULL,
	CONSTRAINT fk_part_measurementhistory_partition FOREIGN KEY (partition_id) REFERENCES intimev2."partition"(id),
	CONSTRAINT fk_part_measurementhistory_provenance_id_provenance_pk FOREIGN KEY (provenance_id) REFERENCES intimev2.provenance(id),
	CONSTRAINT fk_part_measurementhistory_timeseries FOREIGN KEY (timeseries_id) REFERENCES intimev2.timeseries(id)
) partition by list (partition_id);
CREATE INDEX idx_part_measurementhistory_timeseries_ts ON intimev2.part_measurementhistory USING btree (timeseries_id, "timestamp");

-- attach original table as partition for value = default partition
alter table part_measurementhistory attach partition measurementhistory for values in (1);

-- drop redundant constraint
alter table measurementhistory drop constraint ck_measurementhistory_part_1;

-- switch the names around
alter table measurementhistory rename to measurementhistory_1;
alter table part_measurementhistory rename to measurementhistory;

-- normalize indexes and foreign key names 
-- note: foreign key names stay the same throughout all partitions, they are not unique, hence not renaming the partition's FKs
-- index name corresponds to what is created automatically when creating a new partition
alter index idx_measurementhistory_timeseries_ts rename to measurementhistory_1_timeseries_id_timestamp_idx;
alter table measurementhistory rename constraint fk_part_measurementhistory_partition to fk_measurementhistory_partition;
alter table measurementhistory rename constraint fk_part_measurementhistory_provenance_id_provenance_pk to fk_measurementhistory_provenance_id_provenance_pk;
alter table measurementhistory rename constraint fk_part_measurementhistory_timeseries to fk_measurementhistory_timeseries;
alter index idx_part_measurementhistory_timeseries_ts rename to idx_measurementhistory_timeseries_ts;

-- repeat for STRING
ALTER TABLE measurementstringhistory 
    ADD CONSTRAINT ck_measurementstringhistory_part_1 
    CHECK (partition_id = 1)
    not valid;
alter table measurementstringhistory validate constraint ck_measurementstringhistory_part_1;

CREATE TABLE intimev2.part_measurementstringhistory (
	created_on timestamp NOT NULL,
	"timestamp" timestamp NOT NULL,
	string_value text NOT NULL,
	provenance_id int8 NULL,
	timeseries_id int4 NOT NULL,
	partition_id int2 DEFAULT 1 NOT NULL,
	CONSTRAINT fk_part_measurementstringhistory_partition FOREIGN KEY (partition_id) REFERENCES intimev2."partition"(id),
	CONSTRAINT fk_part_measurementstringhistory_provenance_id_provenance_pk FOREIGN KEY (provenance_id) REFERENCES intimev2.provenance(id),
	CONSTRAINT fk_part_measurementstringhistory_timeseries FOREIGN KEY (timeseries_id) REFERENCES intimev2.timeseries(id)
) partition by list (partition_id);
CREATE INDEX idx_part_measurementstringhistory_timeseries_ts ON intimev2.part_measurementstringhistory USING btree (timeseries_id, "timestamp");


alter table part_measurementstringhistory attach partition measurementstringhistory for values in (1);
alter table measurementstringhistory drop constraint ck_measurementstringhistory_part_1;

alter table measurementstringhistory rename to measurementstringhistory_1;
alter table part_measurementstringhistory rename to measurementstringhistory;
alter index idx_measurementstringhistory_timeseries_ts rename to measurementstringhistory_1_timeseries_id_timestamp_idx;
alter table measurementstringhistory rename constraint fk_part_measurementstringhistory_partition to fk_measurementstringhistory_partition;
alter table measurementstringhistory rename constraint fk_part_measurementstringhistory_provenance_id_provenance_pk to fk_measurementstringhistory_provenance_id_provenance_pk;
alter table measurementstringhistory rename constraint fk_part_measurementstringhistory_timeseries to fk_measurementstringhistory_timeseries;
alter index idx_part_measurementstringhistory_timeseries_ts rename to idx_measurementstringhistory_timeseries_ts;


-- repeat for JSON
ALTER TABLE measurementjsonhistory 
    ADD CONSTRAINT ck_measurementjsonhistory_part_1 
    CHECK (partition_id = 1)
    not valid;
alter table measurementjsonhistory validate constraint ck_measurementjsonhistory_part_1;

CREATE TABLE intimev2.part_measurementjsonhistory (
	created_on timestamp NOT NULL,
	"timestamp" timestamp NOT NULL,
	json_value jsonb NULL,
	provenance_id int8 NULL,
	json_value_md5 varchar(32) GENERATED ALWAYS AS (md5(json_value::text)) STORED NULL,
	timeseries_id int4 NULL,
	partition_id int2 DEFAULT 1 NOT NULL,
	CONSTRAINT fk_part_measurementjsonhistory_partition FOREIGN KEY (partition_id) REFERENCES intimev2."partition"(id),
	CONSTRAINT fk_part_measurementjsonhistory_provenance_id_provenance_pk FOREIGN KEY (provenance_id) REFERENCES intimev2.provenance(id),
	CONSTRAINT fk_part_measurementjsonhistory_timeseries FOREIGN KEY (timeseries_id) REFERENCES intimev2.timeseries(id)
) partition by list (partition_id);
CREATE INDEX idx_part_measurementjsonhistory_timeseries_ts ON intimev2.part_measurementjsonhistory USING btree (timeseries_id, "timestamp");

alter table part_measurementjsonhistory attach partition measurementjsonhistory for values in (1);
alter table measurementjsonhistory drop constraint ck_measurementjsonhistory_part_1;

alter table measurementjsonhistory rename to measurementjsonhistory_1;
alter table part_measurementjsonhistory rename to measurementjsonhistory;
alter index idx_measurementjsonhistory_timeseries_ts rename to measurementjsonhistory_1_timeseries_id_timestamp_idx;
alter table measurementjsonhistory rename constraint fk_part_measurementjsonhistory_partition to fk_measurementjsonhistory_partition;
alter table measurementjsonhistory rename constraint fk_part_measurementjsonhistory_provenance_id_provenance_pk to fk_measurementjsonhistory_provenance_id_provenance_pk;
alter table measurementjsonhistory rename constraint fk_part_measurementjsonhistory_timeseries to fk_measurementjsonhistory_timeseries;
alter index idx_part_measurementjsonhistory_timeseries_ts rename to idx_measurementjsonhistory_timeseries_ts; 

grant select on measurementhistory to bdp_readonly;
grant select on measurementstringhistory to bdp_readonly;
grant select on measurementjsonhistory to bdp_readonly;

