// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;


import java.util.Date;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.opendatahub.timeseries.bdp.dto.dto.RecordDto;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

/**
 * Implementation for a list of measurements of type <code>double</code>.
 *
 * <p>Extends {@link MeasurementAbstractHistory}.</p>
 *
 * @author Patrick Bertolla
 */
@Table(
	name = "measurementjsonhistory",
	uniqueConstraints = {
		@UniqueConstraint(
			columnNames = {"station_id", "type_id", "timestamp", "period", "json_value"}
		)
	}
)
@Entity
public class MeasurementJSONHistory extends MeasurementAbstractHistory {

	@Transient
	private static final long serialVersionUID = 3374278433057820376L;

	@Id
	@GeneratedValue(generator = "measurementhistory_json_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "measurementhistory_json_gen", sequenceName = "measurementhistory_json_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('measurementhistory_json_seq')")
	private Long id;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> jsonValue;

	public Map<String, Object> getJsonValue() {
		return jsonValue;
	}

	public void setJsonValue(Map<String, Object> jsonValue) {
		this.jsonValue = jsonValue;
	}

	public MeasurementJSONHistory() {
		super();
	}

	/**
	 * @param station entity associated with this measurement
	 * @param type entity associated with this measurement
	 * @param value number value for this measurement
	 * @param timestamp UTC time of measurement detection
	 * @param period standard interval between 2 measurements
	 */
	public MeasurementJSONHistory(Station station, DataType type, Map<String,Object> value, Date timestamp, Integer period) {
		super(station, type, timestamp, period);
		setValue(value);
	}

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}

	@Override
	public List<RecordDto> findRecords(EntityManager em, String stationtype, String identifier, String cname, Date start, Date end, Integer period) {
		return TimeSeries.findRecordsImpl(em, stationtype, identifier, cname, start, end, period, this);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setValue(Object value) {
		if (value instanceof Map) {
			this.setJsonValue(((Map<String,Object>)value));
		}
	}

	@Override
	public Object getValue() {
		return jsonValue;
	}

}
