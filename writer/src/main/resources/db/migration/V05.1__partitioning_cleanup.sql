-- SPDX-FileCopyrightText: 2025 NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0

set search_path to ${default_schema}, public;

vacuum full measurementstringhistory;
vacuum full measurementstring;
vacuum full measurementjsonhistory;
vacuum full measurementjson;
vacuum full measurementhistory;
vacuum full measurement;