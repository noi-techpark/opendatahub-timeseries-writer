// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019-2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

/**
 * Implementation for a list of measurements of type <code>double</code>.
 *
 * <p>
 * Extends {@link MeasurementAbstractHistory}.
 * </p>
 *
 * @author Patrick Bertolla
 */
@Table(name = "measurementjsonhistory", 
	indexes = { @Index( columnList = "timeseries_id, timestamp") },
	uniqueConstraints = { @UniqueConstraint(columnNames = { "timeseries_id", "timestamp" })}
)
@Entity
public class MeasurementJSONHistory extends MeasurementAbstractHistory {

	@Transient
	private static final long serialVersionUID = 3374278433057820376L;

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

	@Override
	@SuppressWarnings("unchecked")
	public void setValue(Object value) {
		if (value instanceof Map) {
			this.setJsonValue(((Map<String, Object>) value));
		}
	}
	
	@Override
	public Object getValue() {
		return jsonValue;
	}
}
