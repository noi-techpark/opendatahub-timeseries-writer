// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import static net.logstash.logback.argument.StructuredArguments.v;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.hibernate.annotations.ColumnDefault;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opendatahub.timeseries.bdp.writer.dal.util.JPAException;
import com.opendatahub.timeseries.bdp.writer.dal.util.Log;
import com.opendatahub.timeseries.bdp.writer.dal.util.QueryBuilder;
import com.opendatahub.timeseries.bdp.dto.dto.CoordinateDto;
import com.opendatahub.timeseries.bdp.dto.dto.StationDto;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;


/**
 * Station is a point in space, where a measurement occurred. Stations have a name and are categorized in station
 * types. See {@link Station#getStationtype()}.
 *
 * <p> Each station can also have a parent station, like a car can have a car sharing parking-lot as it's parent.
 * Finally, a station holds also a meta data JSON object, that can be anything, that provides additional
 * information about that station.</p>
 *
 * @author Bertolla Patrick
 * @author Peter Moser
 */
@Table(name = "station", uniqueConstraints = @UniqueConstraint(columnNames = { "stationcode", "stationtype" }))
@Entity
public class Station {

	private static final Logger LOG = LoggerFactory.getLogger(Station.class);

	public static final String GEOM_CRS = "EPSG:4326";
	public static final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

	@Id
	@GeneratedValue(generator = "station_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "station_gen", sequenceName = "station_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('station_seq')")
	protected Long id;

	@ManyToOne
	protected Station parent;

	@Column(nullable = false)
	protected String name;

	@Column(nullable = true)
	protected Point pointprojection;

	@Column(nullable = false)
	protected String stationcode;

	@Column(nullable = false)
	protected String stationtype;

	protected Boolean active;

	protected Boolean available;

	private String origin;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "station", fetch = FetchType.LAZY)
	private Collection<MetaData> metaDataHistory;

	@OneToOne
	private MetaData metaData;

	public Station() {
		this.available = true;
		this.active = true;
	}

	/**
	 * @param stationType typology of a {@link Station}
	 * @param stationCode unique identifier of a {@link Station}
	 * @param stationName good chosen name, preferably in English
	 */
	public Station(String stationType, String stationCode, String stationName) {
		this();
		setStationtype(stationType);
		setStationcode(stationCode);
		setName(stationName);
	}

	/**
	 * Queries database on meta data of the specified station. Meta data consists of
	 * defined fields like stationcode(uuid) and optional meta data which gets
	 * versioned and only the newest one is used
	 *
	 * @param em          entity manager
	 * @param stationType typology of a {@link Station}
	 * @param station
	 *
	 * @return detail information/meta data of the specified station(s)
	 */
	public static List<StationDto> findStationsDetails(EntityManager em, String stationType, Station station){
		List<Station> resultList = new ArrayList<>();
		if (station == null)
			resultList = Station.findStations(em, stationType, true);
		else
			resultList.add(station);
		return convertToDto(resultList);
	}

	/**
	 * Takes informations from database fields and all meta data information from JSON field and
	 * converts it to a serializable objects
	 *
	 * @param resultList station entities to convert to valid DTOs
	 *
	 * @return valid StationDto containing serializable informations of the station entity
	 */
	public static List<StationDto> convertToDto(List<Station> resultList) {
		List<StationDto> stationList = new ArrayList<>();
		for (Station s : resultList) {
			StationDto dto = convertToDto(s);
			stationList.add(dto);
		}
		return stationList;
	}


	private static StationDto convertToDto(Station s) {
		Double x = null;
		Double y = null;
		if (s.getPointprojection() != null){
			y = s.getPointprojection().getY();
			x = s.getPointprojection().getX();
		}
		StationDto dto = new StationDto(s.getStationcode(),s.getName(),y,x);
		dto.setCoordinateReferenceSystem(GEOM_CRS);
		dto.setParentStation(s.getParent() == null ? null : s.getParent().getStationcode());
		dto.setOrigin(s.getOrigin());
		if (s.getMetaData() != null)
			dto.setMetaData(s.getMetaData().getJson());
		return dto;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Point getPointprojection() {
		return pointprojection;
	}

	public void setPointprojection(Point pointprojection) {
		this.pointprojection = pointprojection;
	}

	public String getStationcode() {
		return stationcode;
	}

	public void setStationcode(String stationCode) {
		this.stationcode = stationCode;
	}

	/**
	 * <p>Get the category or typology of this station.</p>
	 * For example:
	 * <code>car, parkingLot, weatherStation</code>
	 */
	public String getStationtype() {
		return stationtype;
	}

	public void setStationtype(String stationType) {
		this.stationtype = stationType;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public Boolean getAvailable() {
		return available;
	}

	public void setAvailable(Boolean available) {
		this.available = available;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public Station getParent() {
		return parent;
	}

	public void setParent(Station parent) {
		this.parent = parent;
	}

	public MetaData getMetaData() {
		return metaData;
	}

	public void setMetaData(MetaData metaData) {
		this.metaData = metaData;
	}

	public Collection<MetaData> getMetaDataHistory() {
		return metaDataHistory;
	}

	/**
	 * @param em          entity manager
	 * @param stationType typology of a {@link Station}
	 * @param isActive
	 *
	 * @return list of unique string identifier for each active station of a specific station type
	 */
	public static List<String> findStationCodes(EntityManager em, String stationType, boolean isActive) {
		return em.createQuery("select station.stationcode from Station station where station.active = :active and station.stationtype = :stationtype", String.class)
				 .setParameter("active", isActive)
				 .setParameter("stationtype", stationType)
				 .getResultList();
	}


	/**
	 * @param em          entity manager
	 * @param stationType typology of a {@link Station}
	 * @param isActive    activity state provided by the data collector
	 *
	 * @return			  a list of station entities filtered by their activity state and station typology
	 */
	public static List<Station> findStations(EntityManager em, String stationType, boolean isActive) {
		return em.createQuery("select station from Station station where station.active = :active and station.stationtype = :stationtype", Station.class)
				 .setParameter("active", isActive)
				 .setParameter("stationtype", stationType)
				 .getResultList();
	}
	/**
	 * @param em entity manager
	 *
	 * @return unfiltered station entities
	 */
	public static List<Station> findStations(EntityManager em){
		return em.createQuery("select station from Station station", Station.class)
				 .getResultList();
	}

	/**
	 * @param em entity manager
	 *
	 * @return unique string identifiers for each existing station type
	 */
	public static List<String> findStationTypes(EntityManager em) {
		return em.createQuery("SELECT station.stationtype FROM Station station GROUP BY station.stationtype", String.class)
				 .getResultList();
	}

	/**
	 * @param em entity manager
	 * @param stationType typology of a {@link Station}
	 * @param stationCode unique identifier of a {@link Station}
	 *
	 * @return station entity filtered by station code and station type
	 */
	private static Station findStation(EntityManager em, String stationType, Object stationCode) {
		if(stationCode == null || stationType == null || stationType.isEmpty())
			return null;
		return QueryBuilder
				.init(em)
				.addSql("SELECT station FROM Station station",
						"WHERE station.stationcode = :stationcode AND station.stationtype = :stationtype")
				.setParameter("stationcode", stationCode)
				.setParameter("stationtype", stationType)
				.buildSingleResultOrNull(Station.class);
	}

	public static Station findStation(EntityManager em, String stationType, Integer stationCode) {
		return findStation(em, stationType, (Object) stationCode);
	}

	public static Station findStation(EntityManager em, String stationType, String stationCode) {
		if(stationCode.isEmpty())
			return null;
		return findStation(em, stationType, (Object) stationCode);
	}

	protected static List<String[]> getDataTypesFromQuery(List<Object[]> resultList){
		List<String[]> stringlist = new ArrayList<>();
		for(Object[] objects : resultList){
			String[] stringarray= new String[objects.length];
			for (int i = 0; i< objects.length;i++){
				String value = String.valueOf(objects[i]);
				stringarray[i]= "null".equals(value) ? "" : value;
			}
			stringlist.add(stringarray);
		}
		return stringlist;
	}

	protected static List<CoordinateDto> parseCoordinate(Coordinate[] coordinates) {
		List<CoordinateDto> dtos = new ArrayList<>();
		for (Coordinate coordinate: coordinates){
			dtos.add(parseCoordinate(coordinate));
		}
		return dtos;
	}

	protected static CoordinateDto parseCoordinate(Coordinate coordinate) {
		return new CoordinateDto(coordinate.x,coordinate.y);
	}

	/**
	 * Overrides all stations meta data with the current provided by the specific data collector.
	 * Keep in mind that meta data gets versioned.
	 *
	 * @param em entity manager
	 * @param stationType typology of a {@link Station}
	 * @param data list of station DTOs provided by a data collector
	 */
	public static void syncStations(
		EntityManager em,
		String stationType,
		List<StationDto> data,
		String provenanceName,
		String provenanceVersion,
		boolean syncState,
		boolean onlyActivation
	) {
		if (data == null || data.isEmpty()) {
			return;
		}
		List<String> stationCodes = new ArrayList<>();
		em.getTransaction().begin();
		try {
			for (StationDto dto : data) {
				if (dto.getStationType() == null) {
					dto.setStationType(stationType);
				}
				if (dto.isValid()) {
					if (stationCodes.contains(dto.getId())) {
						LOG.warn(
								"[{}/{}] Station with ID {} already in syncStation list... skipping!",
								provenanceName,
								provenanceVersion,
								dto.getId(),
								v("StationDto", dto));
					} else {
						sync(em, dto);
						stationCodes.add(dto.getId());
					}
				} else {
					LOG.warn(
							"[{}/{}] Invalid JSON for StationDto: {}",
							provenanceName,
							provenanceVersion,
							v("StationDto", dto));
				}
			}
			em.getTransaction().commit();
		} catch (Exception e) {
			throw JPAException.unnest(e);
		} finally {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
		}
		if (syncState) {
			String origin = data.get(0).getOrigin();
			em.getTransaction().begin();
			try {
				syncStationStates(em, stationType, origin, stationCodes, provenanceName, provenanceVersion, onlyActivation);
				em.getTransaction().commit();
			} catch (Exception e) {
				throw JPAException.unnest(e);
			} finally {
				if (em.getTransaction().isActive()) {
					em.getTransaction().rollback();
				}
			}
		}
	}


	/**
	 * @param em entity manager
	 * @param dto station DTO
	 *
	 * @throws JPAException is thrown if geographical transformation from one projection to another fails
	 */
	private static void sync(EntityManager em, StationDto dto) {
		Station existingStation = Station.findStation(em, dto.getStationType(), dto.getId());
		if (existingStation == null) {
			existingStation = new Station();
			existingStation.setStationcode(dto.getId());
			existingStation.setStationtype(dto.getStationType());
			existingStation.setName(dto.getName());
			existingStation.setActive(true);
			em.persist(existingStation);
		}
		if (dto.getLatitude() != null && dto.getLongitude() != null) {
			Point point = geometryFactory.createPoint(new Coordinate(dto.getLongitude(), dto.getLatitude()));
			if (dto.getCoordinateReferenceSystem() != null && !GEOM_CRS.equals(dto.getCoordinateReferenceSystem())) {
				CoordinateReferenceSystem thirdPartyCRS;
				try {
					thirdPartyCRS = CRS.decode(dto.getCoordinateReferenceSystem(), true);
					CoordinateReferenceSystem crs = CRS.decode(GEOM_CRS, true);
					Geometry geometry = JTS.transform(point, CRS.findMathTransform(thirdPartyCRS, crs));
					point = geometry.getCentroid();
				} catch (FactoryException | MismatchedDimensionException | TransformException e) {
					throw new JPAException("Unable to create a valid coordinate reference system for station " + existingStation.getName(), e);
					// FIXME Should an invalid CRS terminate any insertion or continue without?
					// Should we return an message that some station had problems with their CRS, but were nevertheless inserted
				}
			}
			point.setSRID(4326);
			existingStation.setPointprojection(point);
		}
		existingStation.setOrigin(dto.getOrigin());
		if (dto.getParentStation() != null) {
			Station parent = Station.findStationByIdentifier(em, dto.getParentStation());
			if (parent != null)
				existingStation.setParent(parent);
		}
		
		/* We do not need to check for NULL, nor empty strings, because the writer will take care of that */
		existingStation.setName(dto.getName());
		existingStation.setActive(true);

		syncMetaData(em, dto.getMetaData(), existingStation);

		em.merge(existingStation);
	}

	/**
	 * Create a new meta data entry, if it does not yet exist or if it is different from
	 * the previously inserted one.
	 *
	 * @param em entity manager
	 * @param metaData new meta data map provided by the data collector
	 * @param station current entity retrieved from database
	 */
	protected static void syncMetaData(EntityManager em, Map<String, Object> metaData, Station station) {
		if (metaData == null)
			return;

		//Map<String, Object> stationMeta = new HashMap<>();
		//stationMeta.put("code", station.getStationcode());
		//stationMeta.put("name", station.getName());
		//stationMeta.put("origin", station.getOrigin());
		//stationMeta.put("type", station.getOrigin());
		//stationMeta.put("active", station.getOrigin());
		//stationMeta.put("available", station.getOrigin());
		//stationMeta.put("coordinate", Map.of(
		//	"x", station.pointprojection.getX(),
		//	"y", station.pointprojection.getY(),
		//	"srid", station.pointprojection.getSRID()));
		//metaData.put("station", stationMeta);

		boolean metaDataExists = (station.getMetaData() != null && station.getMetaData().getJson() != null);
		if (!metaDataExists || !metaData.equals(station.getMetaData().getJson())) {
			station.setMetaData(metaData);
			em.persist(station.getMetaData());
		}
	}

	/**
	 * Always create a new MetaData, since we want to persist a new record
	 * and not update an existing one, to keep the history of all meta data
	 * changes.
	 *
	 * @param metaData new meta data map
	 */
	private void setMetaData(Map<String, Object> metaData) {
		this.metaData = new MetaData();
		this.metaData.setStation(this);
		this.metaData.setJson(metaData);
	}

	/**
	 * Synchronizes stations state, active stations are provided by a  data collector.
	 * Queries database for stations with a specific origin and if provided, station type.
	 * Deactivates stations in DB which are not in the provided list and activates the ones which are.
	 *
	 * @param em   entity manager
	 * @param dtos active stations, provided by the corresponding data-collector
	 */
	public static void syncActiveOfExistingStations(EntityManager em, List<StationDto> dtos) {
		if (dtos == null || dtos.isEmpty()) {
			return;
		}
		List<Station> stations = findStationsByOrigin(em, dtos.get(0).getOrigin(),dtos.get(0).getStationType());
		if (stations.isEmpty()) {
			return;
		}
		for (Station station : stations){
			boolean isActive = false;
			for (StationDto dto : dtos) {
				if (station.getStationcode().equals(dto.getId())) {
					isActive = true;
					break;
				}
			}
			if (station.getActive() == null || isActive != station.getActive()){
				station.setActive(isActive);
				em.merge(station);
			}
		}
	}

	/**
	 * Synchronizes stations state, active stations are provided by a data
	 * collector. Queries database for stations with a specific origin and if
	 * provided, station type. Deactivates stations in DB which are not in the
	 * provided list and activates the ones which are.
	 *
	 * @param em                entity manager
	 * @param stationType       typology of a {@link Station}
	 * @param origin            name of the source of this data set (optional
	 * 							for compatibility reasons with past versions)
	 * @param stationCodeList   active station IDs, provided by the corresponding
	 * 							data-collector
	 * @param onlyActivation    Do not set other stations in the category to inactive
	 */
	public static int syncStationStates(
		EntityManager em,
		String stationType,
		String origin,
		List<String> stationCodeList,
		String provenanceName,
		String provenanceVersion,
		boolean onlyActivation
	) {
		if (!onlyActivation && (origin == null || origin.isEmpty())) {
			Log
				.init(LOG, "syncStationStates")
				.setProvenance(provenanceName, provenanceVersion)
				.warn("Data Collector does not provide an origin, we update only the stations in the list!");
			onlyActivation = true;
		}

		if (onlyActivation) {
			return QueryBuilder
				.init(em)
				.nativeQuery()
				.addSql("update {h-schema}station s set active = true")
				.addSql("where s.stationtype = :stationtype")
				.addSql("and s.stationcode in :stationlist")
				.addSqlIf("and s.origin is null", origin == null || origin.isEmpty())
				.setParameterIfNotEmpty("origin", origin, "and s.origin = :origin")
				.setParameter("stationtype", stationType)
				.setParameter("stationlist", stationCodeList)
				.buildNative()
				.executeUpdate();
		}

		return QueryBuilder
			.init(em)
			.nativeQuery()
			.addSql("update {h-schema}station s set active = case")
			.addSql("when s.stationcode in :stationlist then true else false end")
			.addSql("where s.stationtype = :stationtype")
			.setParameter("stationlist", stationCodeList)
			.setParameter("stationtype", stationType)
			.setParameterIfNotEmpty("origin", origin, "and s.origin = :origin")
			.buildNative()
			.executeUpdate();
	}

	/**
	 *
	 * @param em entity manager
	 * @param origin data collector identifier where the data origins from
	 * @param stationType typology of a {@link Station}
	 *
	 * @return list of station entities filtered by station type and data collector origin; never <code>null</code>
	 */
	private static List<Station> findStationsByOrigin(EntityManager em, String origin, String stationType) {
		if (origin == null || origin.isEmpty()) {
			return new ArrayList<>();
		}
		QueryBuilder builder = QueryBuilder.init(em)
				.addSql("select station from Station station where station.origin = :origin")
				.setParameter("origin", origin)
				.setParameterIfNotEmpty("stationtype", stationType, " and stationtype = :stationtype");
		List<Station> result = builder.buildResultList(Station.class);
		return result == null ? new ArrayList<>() : result;
	}

	/**
	 * Retrieves and serializes all child stations which have the station with stationId as identifier as their parent
	 *
	 * @param em entity manager
	 * @param stationCode unique identifier of a station
	 *
	 * @return list of station DTOs
	 */
	public List<StationDto> findChildren(EntityManager em, String stationCode) {
		List<Station> stations = em.createQuery("select station from Station station where station.parent.stationcode = :parentId", Station.class)
								   .setParameter("parentId", stationCode)
								   .getResultList();
		return convertToDto(stations);
	}

	public static void patch(EntityManager em, StationDto dto) {
		Station station = Station.findStationByIdentifier(em,dto.getId());
		em.merge(station);
	}

	private static Station findStationByIdentifier(EntityManager em, String id) {
		return QueryBuilder
				.init(em)
				.addSql("SELECT s FROM Station s WHERE s.stationcode = :stationcode")
				.setParameter("stationcode", id)
				.buildSingleResultOrNull(Station.class);
	}

	/**
	 * @param em entity manager
	 * @param stationType typology of a {@link Station}
	 * @param origin string identifier of the data collector from where data origins
	 * @return list of station entities filtered by station type and origin
	 */
	public static List<Station> findStations(EntityManager em, String stationType, String origin) {
		try {
			return QueryBuilder
					.init(em)
					.addSql("SELECT station FROM Station station",
							"WHERE station.active = :active AND station.stationtype = :type")
					.setParameterIf("origin", origin, "AND origin = :origin", origin!=null && !origin.isEmpty())
					.setParameter("active", true)
					.setParameter("type", stationType)
					.buildResultList(Station.class);
		} catch (Exception e) {
			throw new JPAException("Unable to create query for station type '" + stationType + "'", e);
		}
	}

}
