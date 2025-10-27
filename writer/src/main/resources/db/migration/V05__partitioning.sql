-- SPDX-FileCopyrightText: 2025 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0
set search_path to ${default_schema}, public;
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

update measurementstringhistory h
set timeseries_id = t.id, partition_id = 1
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurementstring';

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

update measurementhistory h
set timeseries_id = t.id, partition_id = 1
from timeseries t
where t.station_id = h.station_id 
and t.type_id = h.type_id 
and t.period = h."period" 
and t.value_table = 'measurement';

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