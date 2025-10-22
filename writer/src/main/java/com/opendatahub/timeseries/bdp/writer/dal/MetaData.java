// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import java.util.Date;
import java.util.Map;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.opendatahub.timeseries.bdp.dto.dto.StationDto;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * <p>
 * MetaData is a versioned JSONB map containing all additional information for a<br/>
 * {@link Station}. If a data collector provides a different meta data object it<br/>
 * will replace the current one shown as meta data through the API.
 * </p>
 *
 * @author Peter Moser
 * @author Patrick Bertolla
 *
 */
@Entity
@Table(name = "metadata", 
	indexes = @Index(columnList = "station_id, created_on")
)
public class MetaData {

	@Id
	@GeneratedValue(generator = "metadata_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "metadata_gen", sequenceName = "metadata_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('metadata_seq')")
	protected Long id;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> json;

	@ManyToOne
	private Station station;

	private Date created_on;

	public MetaData() {
		created_on = new Date();
	}

	public Map<String, Object> getJson() {
		return json;
	}

	/**
	 * Set JSON data (= meta data). We do not eliminate null values here,
	 * because we want to set values to null to remove them from the result.
	 * For instance, when retrieving a {@link StationDto}.
	 *
	 * @param metaData a key/object map, containing whatever you want
	 */
	public void setJson(Map<String, Object> metaData) {
		this.json = metaData;
	}

	public Station getStation() {
		return station;
	}

	public void setStation(Station station) {
		this.station = station;
	}

	public Date getCreated() {
		return created_on;
	}
}
