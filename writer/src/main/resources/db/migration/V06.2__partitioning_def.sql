-- SPDX-FileCopyrightText: 2025 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0
ALTER TABLE partition_def DROP CONSTRAINT uc_partition_def;

ALTER TABLE partition_def 
ADD CONSTRAINT uc_partition_def UNIQUE NULLS NOT DISTINCT (origin, stationtype, type_id, period);