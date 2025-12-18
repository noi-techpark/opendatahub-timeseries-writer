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

-- migrate latest tables
\echo 'migrate latest tables'
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

alter table measurementjson add column timeseries_id int4;
alter table measurementjson add constraint fk_measurementjson_timeseries foreign key (timeseries_id) references timeseries(id);
create unique index idx_measurementjson_timeseries_ts on measurementjson (timeseries_id);
update measurementjson h
set timeseries_id = t.id
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurementjson';
alter table measurementjson alter column timeseries_id set not null;

alter table measurement add column timeseries_id int4;
alter table measurement add constraint fk_measurement_timeseries foreign key (timeseries_id) references timeseries(id);
create unique index idx_measurement_timeseries_ts on measurement (timeseries_id);
update measurement h
set timeseries_id = t.id
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurement';
alter table measurement alter column timeseries_id set not null;

-- remove old columns from latest tables
alter table measurementstring drop column id;
alter table measurementstring drop column station_id;
alter table measurementstring drop column type_id;
alter table measurementstring drop column period;
alter table measurementjson drop column id;
alter table measurementjson drop column station_id;
alter table measurementjson drop column type_id;
alter table measurementjson drop column period;
alter table measurement drop column id;
alter table measurement drop column station_id;
alter table measurement drop column type_id;
alter table measurement drop column period;

-- drop old history tables
\echo 'drop history tables'
drop table measurementstringhistory cascade;
drop table measurementjsonhistory cascade;
drop table measurementhistory cascade;

-- drop associated primary key sequences
drop sequence measurementstring_seq;
drop sequence measurementstringhistory_seq;
drop sequence measurement_json_seq;
drop sequence measurementhistory_json_seq;
drop sequence measurement_seq;
drop sequence measurementhistory_seq;

-- remove functions used for (now deleted) triggers
drop function sync_measurementstringhistory;
drop function sync_measurementjsonhistory;
drop function sync_measurementhistory;

-- rename new history tables to final names including constraints and indexes
alter table new_measurementstringhistory rename to measurementstringhistory;
alter table new_measurementjsonhistory rename to measurementjsonhistory;
alter table new_measurementhistory rename to measurementhistory;

alter table measurementhistory rename constraint fk_new_measurementhistory_partition                         to  fk_measurementhistory_partition;
alter table measurementhistory rename constraint fk_new_measurementhistory_provenance_id_provenance_pk       to  fk_measurementhistory_provenance_id_provenance_pk;
alter table measurementhistory rename constraint fk_new_measurementhistory_timeseries                        to  fk_measurementhistory_timeseries;
alter table measurementstringhistory rename constraint fk_new_measurementstringhistory_partition                   to  fk_measurementstringhistory_partition;
alter table measurementstringhistory rename constraint fk_new_measurementstringhistory_provenance_id_provenance_pk to  fk_measurementstringhistory_provenance_id_provenance_pk;
alter table measurementstringhistory rename constraint fk_new_measurementstringhistory_timeseries                  to  fk_measurementstringhistory_timeseries;
alter table measurementjsonhistory rename constraint fk_new_measurementjsonhistory_partition                     to  fk_measurementjsonhistory_partition;
alter table measurementjsonhistory rename constraint fk_new_measurementjsonhistory_provenance_id_provenance_pk   to  fk_measurementjsonhistory_provenance_id_provenance_pk;
alter table measurementjsonhistory rename constraint fk_new_measurementjsonhistory_timeseries                    to  fk_measurementjsonhistory_timeseries;

alter index idx_new_measurementstringhistory_timeseries_ts rename to idx_measurementstringhistory_timeseries_ts;
alter index idx_new_measurementjsonhistory_timeseries_ts rename to idx_measurementjsonhistory_timeseries_ts;
alter index idx_new_measurementhistory_timeseries_ts rename to idx_measurementhistory_timeseries_ts; 

-- vacuum full station, provenance, type, event all latest tables
\echo 'vacuum'
vacuum full station;
vacuum full provenance;
vacuum full type;
vacuum full event;
vacuum full measurement;
vacuum full measurementstring;
vacuum full measurementjson;

-- flag flyway migrations as applied
INSERT INTO flyway_schema_history (installed_rank, "version", description, "type", script, checksum, installed_by, installed_on, execution_time, success)
VALUES(5, '05', 'timeseries', 'SQL', 'V05__timeseries.sql', -718283751, 'clezag', now(), 0, true);

INSERT INTO flyway_schema_history (installed_rank, "version", description, "type", script, checksum, installed_by, installed_on, execution_time, success)
VALUES(6, '05.1', 'timeseries cleanup', 'SQL', 'V05.1__timeseries_cleanup.sql', 726016081, 'clezag', now(), 0, true);

INSERT INTO intimev2.flyway_schema_history (installed_rank, "version", description, "type", script, checksum, installed_by, installed_on, execution_time, success)
VALUES(7, '06', 'partitioning', 'SQL', 'V06__partitioning.sql', 1462489619, 'clezag', now(), 0, true);

\echo 'JOB DONE'

