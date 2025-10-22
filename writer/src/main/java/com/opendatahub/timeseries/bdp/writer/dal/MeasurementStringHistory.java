// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019-2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

@Table(name = "measurementstringhistory", 
	indexes = { @Index( columnList = "timeseries_id, timestamp") },
	uniqueConstraints = { @UniqueConstraint(columnNames = { "timeseries_id", "timestamp" })}
)
@Entity
public class MeasurementStringHistory extends MeasurementAbstractHistory {

	@Transient
	private static final long serialVersionUID = 8968054299664379971L;

    /*
     * Make sure all subclasses of MHistory contain different value names. If these
     * variable names would be called the same, but with different data types
     * Hibernate would complain about not being able to create a SQL UNION.
     * Ex. private String value; and private Double value; would not work
     *     inside MeasurementStringHistory and MeasurementHistory respectively
     */
	@Column(nullable = false)
	private String stringValue;

	public MeasurementStringHistory() {
		super();
	}

	@Override
	public String getValue() {
		return stringValue;
	}

	public void setValue(String value) {
		this.stringValue = value;
	}

	@Override
	public void setValue(Object value) {
		if (value != null)
			setValue(value.toString());
	}

}
