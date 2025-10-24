// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import org.hibernate.annotations.ColumnDefault;
import org.locationtech.jts.geom.Geometry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Table(name = "location")
@Entity
public class Location {

	@Id
	@GeneratedValue(generator = "event_location_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "event_location_gen", sequenceName = "event_location_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('event_location_seq')")
	protected Long id;

	@Column(nullable = true)
	protected Geometry geometry;

	@Column(columnDefinition = "TEXT")
	private String description;

	public Geometry getGeometry() {
		return geometry;
	}

	public void setGeometry(Geometry geometry) {
		this.geometry = geometry;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
