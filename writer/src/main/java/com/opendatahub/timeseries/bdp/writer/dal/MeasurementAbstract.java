// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019-2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import com.opendatahub.timeseries.bdp.writer.dal.util.QueryBuilder;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
 * @author Peter Moser
 * @author Patrick Bertolla
 * @author Clemens Zagler
 */
@MappedSuperclass
public abstract class MeasurementAbstract implements Serializable {
	private static final long serialVersionUID = 1L;

	// Measurements are 1:1 to timeseries, so we're using that as primary key
	@Id
	@ManyToOne(cascade = CascadeType.PERSIST, optional = false)
    @JoinColumn(name = "timeseries_id")
	private TimeSeries timeseries;

	@Column(nullable = false)
	private Date created_on;

	@Column(nullable = false)
	private Date timestamp;

	@ManyToOne(optional = true, fetch = FetchType.LAZY)
	private Provenance provenance;

	public abstract MeasurementAbstract findLatestEntry(EntityManager em, Station station, DataType type, Integer period);

	public abstract Date getDateOfLastRecord(EntityManager em, Station station, DataType type, Integer period);

	public abstract void setValue(Object value);

	public abstract Object getValue();

	protected MeasurementAbstract() {
		this.created_on = new Date();
	}

	protected MeasurementAbstract(TimeSeries timeseries, Date timestamp) {
		this.timestamp = timestamp;
		this.timeseries = timeseries;
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

	public Provenance getProvenance() {
		return provenance;
	}

	public void setProvenance(Provenance provenance) {
		this.provenance = provenance;
	}

	public TimeSeries getTimeseries() {
		return timeseries;
	}

	public void setTimeseries(TimeSeries timeseries) {
		this.timeseries = timeseries;
	}

	public static List<MeasurementAbstract> findLatest(EntityManager em, Collection<Station> stations, Collection<DataType> types) {
		List<MeasurementAbstract> ret = new ArrayList<>();
		ret.addAll(findLatestConcrete(Measurement.class, em, stations, types));
		ret.addAll(findLatestConcrete(MeasurementJSON.class, em, stations, types));
		ret.addAll(findLatestConcrete(MeasurementString.class, em, stations, types));
		return ret;
	}

	private static <T extends MeasurementAbstract> List<T> findLatestConcrete(Class<T> clazz, EntityManager em, Collection<Station> stations, Collection<DataType> types) {
		var query = em.createQuery(
				"SELECT record FROM " + clazz.getSimpleName() + " record" +
				" JOIN record.timeseries ts" +
				" WHERE ts.station in (:stations)" +
				" AND ts.type in (:types)"
		, clazz);
		query.setParameter("stations", stations);
		query.setParameter("types", types);
		EntityGraph<T> graph = em.createEntityGraph(clazz);
		graph.addAttributeNodes("timeseries");
		var st = graph.addSubgraph("timeseries");
		st.addAttributeNodes("type");
		st.addAttributeNodes("station");
		query.setHint("jakarta.persistence.fetchgraph", graph);
		return query.getResultList();
	}
}
