// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019-2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import java.util.Date;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
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
 *  Implementation for measurements of type string
 *
 * @author Peter Moser
 * @author Patrick Bertolla
 */
@Table(
	name = "measurementstring",
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
public class MeasurementString extends MeasurementAbstract {

	@Transient
	private static final long serialVersionUID = -4378235887347510723L;

	@Id
	@GeneratedValue(generator = "measurementstring_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "measurementstring_gen", sequenceName = "measurementstring_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('measurementstring_seq')")
	private Long id;

    /*
     * Make sure all subclasses of M contain different value names. If these
     * variable names would be called the same, but with different data types
     * Hibernate would complain about not being able to create a SQL UNION.
     * Ex. private String value; and private Double value; would not work
     *     inside MeasurementString and Measurement respectively
     */
	@Column(nullable = false)
	private String stringValue;

	public MeasurementString() {
		super();
	}

	public MeasurementString(TimeSeries timeseries, String value, Date timestamp) {
		this.setTimestamp(timestamp);
		this.setCreated_on(new Date());
		this.setTimeseries(timeseries);
		this.stringValue = value;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getValue() {
		return stringValue;
	}

	@Override
	public void setValue(Object value) {
		if (value instanceof String)
			this.stringValue = (String) value;
	}

	@Override
	public MeasurementAbstract findLatestEntry(EntityManager em, Station station, DataType type, Integer period) {
		return TimeSeries.findLatestEntryImpl(em, station, type, period, this);
	}

	@Override
	public Date getDateOfLastRecord(EntityManager em, Station station, DataType type, Integer period) {
		return TimeSeries.getDateOfLastRecordImpl(em, station, type, period, this);
	}
}
