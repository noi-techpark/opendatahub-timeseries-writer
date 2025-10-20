// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * <p>
 * This entity contains always the <strong>newest entry of a specific
 * station, type and period</strong>.
 * You will find all historic data in the class
 * {@link MeasurementAbstractHistory}. Each
 * measurement <strong>must</strong> extend this base class to keep integrity.
 *
 * <p>
 * It contains the 2 most important references to station and type and
 * also utility queries for all measurements.
 *
 * @author Peter Moser
 * @author Patrick Bertolla
 */
@MappedSuperclass
public abstract class MeasurementAbstract implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(nullable = false)
	private Date created_on;

	@Column(nullable = false)
	private Date timestamp;

	@ManyToOne(cascade = CascadeType.ALL, optional = false)
	private Station station;

	@ManyToOne(cascade = CascadeType.PERSIST, optional = false)
	private DataType type;

	@Column(nullable = false)
	private Integer period;

	@ManyToOne(optional = true, fetch = FetchType.LAZY)
	private Provenance provenance;

	public abstract MeasurementAbstract findLatestEntry(EntityManager em, Station station, DataType type,
			Integer period);

	public abstract Date getDateOfLastRecord(EntityManager em, Station station, DataType type, Integer period);

	public abstract void setValue(Object value);

	public abstract Object getValue();

	protected MeasurementAbstract() {
		this.created_on = new Date();
	}

	/**
	 * @param station   entity the measurement refers to
	 * @param type      entity the measurement refers to
	 * @param timestamp UTC time of the measurement detection
	 * @param period    standard interval between 2 measurements
	 */
	protected MeasurementAbstract(Station station, DataType type, Date timestamp, Integer period) {
		this.station = station;
		this.type = type;
		this.timestamp = timestamp;
		this.period = period;
		this.created_on = new Date();
	}

	public Date getCreated_on() {
		return created_on;
	}

	public void setCreated_on(Date created_on) {
		this.created_on = created_on;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	public Station getStation() {
		return station;
	}

	public void setStation(Station station) {
		this.station = station;
	}

	public DataType getType() {
		return type;
	}

	public void setType(DataType type) {
		this.type = type;
	}

	public Integer getPeriod() {
		return period;
	}

	public void setPeriod(Integer period) {
		this.period = period;
	}

	public Provenance getProvenance() {
		return provenance;
	}

	public void setProvenance(Provenance provenance) {
		this.provenance = provenance;
	}
}
