create sequence measurement_json_seq start 1 increment 1;
create sequence measurementhistory_json_seq start 1 increment 1;
create table measurementjson (id int8 default nextval('measurement_json_seq') not null, created_on timestamp not null, period int4 not null, timestamp timestamp not null, json_value jsonb, provenance_id int8, station_id int8 not null, type_id int8 not null, primary key (id));
create table measurementjsonhistory (id int8 default nextval('measurementhistory_json_seq') not null, created_on timestamp not null, period int4 not null, timestamp timestamp not null, json_value jsonb, provenance_id int8, station_id int8 not null, type_id int8 not null, primary key (id));
create index idx_measurementjson_timestamp on measurementjson (timestamp desc);
alter table measurementjson add constraint uc_measurementjson_station_id_type_id_period unique (station_id, type_id, period);
alter table measurementjsonhistory add constraint uc_measurementjsonhistory_stati_id_timestamp_period_json_value_ unique (station_id, type_id, timestamp, period, json_value);
alter table measurementjson add constraint fk_measurementjson_provenance_id_provenance_pk foreign key (provenance_id) references provenance;
alter table measurementjson add constraint fk_measurementjson_station_id_station_pk foreign key (station_id) references station;
alter table measurementjson add constraint fk_measurementjson_type_id_type_pk foreign key (type_id) references type;
alter table measurementjsonhistory add constraint fk_measurementjsonhistory_provenance_id_provenance_pk foreign key (provenance_id) references provenance;
alter table measurementjsonhistory add constraint fk_measurementjsonhistory_station_id_station_pk foreign key (station_id) references station;
alter table measurementjsonhistory add constraint fk_measurementjsonhistory_type_id_type_pk foreign key (type_id) references type;
