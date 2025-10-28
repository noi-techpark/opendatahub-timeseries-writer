
-- SPDX-FileCopyrightText: 2025 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0
set search_path=intimev2,public;

-- set session parameters
SET work_mem = '10GB';
SET maintenance_work_mem = '20GB';
SET max_parallel_workers_per_gather = 4;

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

grant select on measurementhistory to bdp_readonly;

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

