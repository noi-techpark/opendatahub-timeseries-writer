// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019-2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;


import java.util.Date;
import java.util.Map;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

/**
 * Implementation for measurements cache of type json
 *
 * @author Patrick Bertolla
 */
@Table(
	name = "measurementjson",
	indexes = {
		@Index(
			columnList = "timeseries_id, timestamp"
		)
	},
	uniqueConstraints = {
		@UniqueConstraint(
			columnNames = {"timeseries_id", "timestamp"}
		)
	}
)
@Entity
public class MeasurementJSON extends MeasurementAbstract {

	@Transient
	private static final long serialVersionUID = 8498633392410463424L;

	@Id
	@GeneratedValue(generator = "measurement_json_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "measurement_json_gen", sequenceName = "measurement_json_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('measurement_json_seq')")
	private Long id;


	public MeasurementJSON() {
	}

	public MeasurementJSON(TimeSeries timeseries, Map<String, Object> json, Date timestamp) {
		super(timeseries,timestamp);
		this.jsonValue = json;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> jsonValue;

	public Map<String, Object> getValue() {
		return jsonValue;
	}

	public void setJsonValue(Map<String, Object> jsonValue) {
		this.jsonValue = jsonValue;
	}

	@Override
	public MeasurementAbstract findLatestEntry(EntityManager em, Station station, DataType type, Integer period) {
		return TimeSeries.findLatestEntryImpl(em, station, type, period, this);
	}

	@Override
	public Date getDateOfLastRecord(EntityManager em, Station station, DataType type, Integer period) {
		return TimeSeries.getDateOfLastRecordImpl(em, station, type, period, this);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setValue(Object value) {
		if (value instanceof Map) {
			this.setJsonValue((Map<String,Object>) value);
		}
	}
}
