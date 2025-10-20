// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019-2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;


import java.util.Date;

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

import org.hibernate.annotations.ColumnDefault;

/**
 * Implementation for measurements cache of type double
 *
 * @author Peter Moser
 * @author Patrick Bertolla
 */
@Table(
	name = "measurement",
	indexes = {
		@Index(
			columnList = "timeseries_id, timestamp"
		)
	},
	uniqueConstraints = {
		@UniqueConstraint(
			columnNames = {"timeseries_id timestamp"}
		)
	}
)
@Entity
public class Measurement extends MeasurementAbstract {

	@Transient
	private static final long serialVersionUID = 2900270107783989197L;

    @Id
	@GeneratedValue(generator = "measurement_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "measurement_gen", sequenceName = "measurement_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('measurement_seq')")
	private Long id;

    /*
     * Make sure all subclasses of M contain different value names. If these
     * variable names would be called the same, but with different data types
     * Hibernate would complain about not being able to create a SQL UNION.
     * Ex. private String value; and private Double value; would not work
     *     inside MeasurementString and Measurement respectively
     */
    @Column(nullable = false)
	private Double doubleValue;

	public Measurement() {
	}

	public Measurement(TimeSeries timeseries, Double value, Date timestamp) {
		super(timeseries,timestamp);
		this.doubleValue = value;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Double getValue() {
		return doubleValue;
	}

	@Override
	public void setValue(Object value) {
		if (value instanceof Double) {
			this.doubleValue = (Double) value;
		} else if (value instanceof Number) {
			this.doubleValue = ((Number) value).doubleValue();
		}
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
