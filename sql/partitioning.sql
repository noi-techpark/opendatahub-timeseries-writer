
-- SPDX-FileCopyrightText: 2025 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0
set search_path=intimev2,public;

-- set session parameters
SET work_mem = '10GB';
SET maintenance_work_mem = '20GB';
SET max_parallel_workers_per_gather = 4;
SET synchronous_commit = OFF;


-- create check constraint corresponding to the partition key, so that during attachment now (exclusive lock) validation has to be done
ALTER TABLE measurementhistory 
    ADD CONSTRAINT ck_measurementhistory_part_1 
    CHECK (partition_id = 1)
    not valid;
-- create and validate separately to avoid exclusive locks
alter table measurementhistory validate constraint ck_measurementhistory_part_1;

-- create partition main table part_measurementhistory as exact clone of measurementhistory
-- pg_dump -U bdp -W -h partition.czracduepxal.eu-west-1.rds.amazonaws.com -d bdp -t intimev2.measurementhistory --schema-only
CREATE TABLE intimev2.part_measurementhistory (
	created_on timestamp NOT NULL,
	"timestamp" timestamp NOT NULL,
	double_value float8 NOT NULL,
	provenance_id int8 NULL,
	timeseries_id int4 NULL,
	partition_id int2 DEFAULT 1 NOT NULL,
	CONSTRAINT fk_part_measurementhistory_partition FOREIGN KEY (partition_id) REFERENCES intimev2."partition"(id),
	CONSTRAINT fk_part_measurementhistory_provenance_id_provenance_pk FOREIGN KEY (provenance_id) REFERENCES intimev2.provenance(id),
	CONSTRAINT fk_part_measurementhistory_timeseries FOREIGN KEY (timeseries_id) REFERENCES intimev2.timeseries(id)
);
CREATE INDEX idx_part_measurementhistory_timeseries_ts ON intimev2.part_measurementhistory USING btree (timeseries_id, "timestamp");

-- attach original table as partition for value = default partition
alter table measurementhistory attach partition part_measurementhistory for values in (1);

-- drop redundant constraint
alter table measurementhistory drop constraint ck_measurementhistory_part_1;

-- switch the names around
alter table measurementhistory rename to measurementhistory_1;
alter table part_measurementhistory to measurementhistory;

-- TODO: rename all the indexes and constraints accordingly



