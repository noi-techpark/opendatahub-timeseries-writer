-- SPDX-FileCopyrightText: 2025 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0

CREATE SEQUENCE partition_def_seq INCREMENT BY 1 start 1;

CREATE TABLE partition_def (
	id int4 DEFAULT nextval('partition_def_seq'::regclass) NOT NULL,
	partition_id int2 NOT NULL,
	origin varchar(255),
	stationtype varchar(255),
	type_id int8,
	"period" int4,
	CONSTRAINT partition_def_pkey PRIMARY KEY (id),
	CONSTRAINT uc_partition_def UNIQUE (origin, stationtype, type_id, period),
	CONSTRAINT fk_partition_def_partition FOREIGN KEY (partition_id) REFERENCES intimev2."partition"(id),
	CONSTRAINT fk_partition_def_type FOREIGN KEY (type_id) REFERENCES intimev2."type"(id)
);
create index IDX_partition_def_origin on partition_def(origin);
create index IDX_partition_def_stationtype on partition_def(stationtype);
create index IDX_partition_def_type on partition_def(type_id);
create index IDX_partition_def_period on partition_def(period);