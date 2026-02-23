// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Type;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import com.opendatahub.timeseries.bdp.dto.dto.EventDto;
import com.opendatahub.timeseries.bdp.writer.dal.util.QueryBuilder;

import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType;
import io.hypersistence.utils.hibernate.type.range.Range;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Table(
	name = "event",
	uniqueConstraints = {
		@UniqueConstraint(columnNames = {"uuid"})
	}
)
@Entity
public class Event {

	private static WKTReader wktReader = new WKTReader(Station.geometryFactory);

	@Id
	@GeneratedValue(generator = "event_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "event_gen", sequenceName = "event_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('event_seq')")
	protected Long id;

	// Unique inside the event table
	@Column(nullable = false)
	private String uuid;

	// Hierarchy of events: DB columns = [ origin, event_series_uuid, name ]
	@Column(nullable = false)
	private String origin;

	@Column(nullable = false)
	private String category;

	@Column(nullable = false)
	private String eventSeriesUuid;

	@Column(nullable = false)
	private String name;

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEventSeriesUuid() {
		return this.eventSeriesUuid;
	}

	public void setEventSeriesUuid(String eventSeriesId) {
		this.eventSeriesUuid = eventSeriesId;
	}

	private Date createdOn;

	@Type(PostgreSQLRangeType.class)
	private Range<LocalDateTime> eventInterval;

	@ManyToOne
	private Location location;

	@OneToOne
	private MetaData metaData;

	@ManyToOne(optional = true, fetch = FetchType.LAZY)
	private Provenance provenance;

	@Column(columnDefinition = "TEXT")
	private String description;

	public Provenance getProvenance() {
		return provenance;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public void setProvenance(Provenance provenance) {
		this.provenance = provenance;
	}

	public Event() {
		setCreatedOn(new Date());
	}
	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public Date getCreatedOn() {
		return createdOn;
	}

	public void setCreatedOn(Date createdOn) {
		this.createdOn = createdOn;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Range<LocalDateTime> getEventInterval() {
		return eventInterval;
	}

	/**
	 * Set the event interval as follows:
	 *
	 * <code>[lower-bound, upper-bound)</code>
	 *
	 * That is, a half-open interval, which is useful for moving window aggregates,
	 * because each window does not overlap with the next, which might happen
	 * with a closed interval on boundaries. It is possible that we have unbound
	 * limits, that is, they are infinite.
	 *
	 * @param eventInterval
	 */
	public void setEventInterval(Range<LocalDateTime> eventInterval) {
		if (
			(!eventInterval.isLowerBoundClosed() && !eventInterval.hasMask(Range.LOWER_INFINITE))
			|| eventInterval.isUpperBoundClosed()
		) {
			throw new IllegalArgumentException(
				"The interval must be half-open [a,b) or unbounded. Given = "
				+ eventInterval.asString()
			);
		}
		this.eventInterval = eventInterval;
	}

	public void setEventInterval(final String eventInterval) {
		try {
			setEventInterval(Range.localDateTimeRange(eventInterval));
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Illegal interval boundaries. Given input: " + eventInterval, ex);
		}
	}

	public MetaData getMetaData() {
		return metaData;
	}

	public void setMetaData(MetaData metaData) {
		this.metaData = metaData;
	}

	public Location getLocation() {
		return location;
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	public static void pushEvents(EntityManager em, List<EventDto> eventDtos) {
		if (eventDtos == null || eventDtos.isEmpty()) {
			return;
		}
		Provenance provenance = Provenance.findByUuid(em, eventDtos.get(0).getProvenance());
		for (EventDto dto : eventDtos) {
			// avoid saving the same event multiple times
			if (Event.find(em, dto.getUuid()) != null)
				continue;
			Event event = new Event();
			event.setUuid(dto.getUuid());
			event.setOrigin(dto.getOrigin());
			event.setCategory(dto.getCategory());
			event.setEventSeriesUuid(dto.getEventSeriesUuid());
			event.setName(dto.getName());
			event.setDescription(dto.getDescription());
			event.setProvenance(provenance);
			event.setEventInterval(dto.getEventIntervalAsString());
			if (!dto.getMetaData().isEmpty()) {
				MetaData metaData = new MetaData();
				Map<String, Object> json = new HashMap<>();
				if (!json.equals(dto.getMetaData())) {
					json.putAll(dto.getMetaData());
					metaData.setJson(json);
					event.setMetaData(metaData);
					em.persist(metaData);
				}
			}
			if (dto.getWktGeometry() != null) {
				Location loc = new Location();
				try {
					loc.setGeometry(wktReader.read(dto.getWktGeometry()));
				} catch (ParseException e) {
					// Ignored
				}
				loc.setDescription(dto.getLocationDescription());
				event.setLocation(loc);
				em.persist(loc);
			}
			em.persist(event);
		}
	}

	public static Event find(EntityManager em, String uuid) {
		return QueryBuilder
			.init(em)
			.addSql("SELECT e FROM Event e where 1=1")
			.setParameterIfNotEmpty("uuid", uuid, "and uuid = :uuid")
			.buildSingleResultOrNull(Event.class);
	}

}
