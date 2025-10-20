// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;


import java.util.Date;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.ColumnDefault;

import com.opendatahub.timeseries.bdp.dto.dto.RecordDto;

/**
 * Implementation for a list of measurements of type <code>double</code>.
 *
 * <p>Extends {@link MeasurementAbstractHistory}.</p>
 *
 * @author Peter Moser
 * @author Patrick Bertolla
 */
@Table(
	name = "measurementhistory",
	uniqueConstraints = {
		@UniqueConstraint(
			columnNames = {"station_id", "type_id", "timestamp", "period", "double_value"}
		)
	}
)
@Entity
public class MeasurementHistory extends MeasurementAbstractHistory {
	@Transient
	private static final long serialVersionUID = 2900270107783989197L;

    @Id
	@GeneratedValue(generator = "measurementhistory_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "measurementhistory_gen", sequenceName = "measurementhistory_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('measurementhistory_seq')")
	private Long id;

    /*
     * Make sure all subclasses of MHistory contain different value names. If these
     * variable names would be called the same, but with different data types
     * Hibernate would complain about not being able to create a SQL UNION.
     * Ex. private String value; and private Double value; would not work
     *     inside MeasurementStringHistory and MeasurementHistory respectively
     */
    @Column(nullable = false)
	private Double doubleValue;

	public MeasurementHistory() {
		super();
	}

	/**
	 * @param station entity associated with this measurement
	 * @param type entity associated with this measurement
	 * @param value number value for this measurement
	 * @param timestamp UTC time of measurement detection
	 * @param period standard interval between 2 measurements
	 */
	public MeasurementHistory(Station station, DataType type, Double value, Date timestamp, Integer period) {
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
	public Double getValue() {
		return doubleValue;
	}

	public void setValue(Double value) {
		this.doubleValue = value;
	}

	@Override
	public List<RecordDto> findRecords(EntityManager em, String stationtype, String identifier, String cname, Date start, Date end, Integer period) {
		return TimeSeries.findRecordsImpl(em, stationtype, identifier, cname, start, end, period, this);
	}
	@Override
	public void setValue(Object value) {
		this.setValue((Double)value);
	}

}
