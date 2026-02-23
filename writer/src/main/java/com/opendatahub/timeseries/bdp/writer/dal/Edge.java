// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import org.hibernate.annotations.ColumnDefault;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * This class is a model of an edge with meta data.
 *
 * <p>We do not want to change the API for v2, hence we need to keep edges
 * as stations to retrieve "edge labels" with station API calls. This means that we use
 * "station" as container class.
 *
 * <p> An edge is therefore a station-triple like <code>(origin, destination, edge_data)</code>,
 * and some additional information, like directed, which describes if the edge has a direction, and
 * a line-geometry, which describe trajectories on a map. Some polylines on a map are not connected
 * to any station, therefore we need to make "origin" and "destination" optional, that is, nullable.
 *
 * @author Peter Moser
 */
@Table(name = "edge")
@Entity
public class Edge {

	public static final String GEOM_CRS = "EPSG:4326";
	public static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(
		new PrecisionModel(),
		4326
	);

	@Id
	@GeneratedValue(generator = "edge_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(
		name = "edge_gen",
		sequenceName = "edge_seq",
		allocationSize = 1
	)
	@ColumnDefault(value = "nextval('edge_seq')")
	protected Long id;

	@Column(nullable = true)
	protected LineString linegeometry;

	@Column(nullable = false)
	@ColumnDefault(value = "true")
	protected boolean directed;

	@ManyToOne(optional = true)
	protected Station origin;

	@ManyToOne(optional = true)
	protected Station destination;

	/*
	 * FIXME This should be changed to use meta data separately, and point from edges
	 * and stations (=nodes) to it directly. We must think of how to group historical
	 * meta data records inside the meta data table itself.
	 *
	 * FIXME This should have an unique constraint, currently set manually on the DB
	 */
	@OneToOne(optional = true)
	protected Station edgeData;
}
