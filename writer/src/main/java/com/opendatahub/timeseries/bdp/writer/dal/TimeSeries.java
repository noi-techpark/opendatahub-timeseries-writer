// Copyright © 2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.hibernate.annotations.ColumnDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opendatahub.timeseries.bdp.dto.dto.DataMapDto;
import com.opendatahub.timeseries.bdp.dto.dto.RecordDto;
import com.opendatahub.timeseries.bdp.dto.dto.RecordDtoImpl;
import com.opendatahub.timeseries.bdp.dto.dto.SimpleRecordDto;
import com.opendatahub.timeseries.bdp.writer.dal.util.JPAException;
import com.opendatahub.timeseries.bdp.writer.dal.util.Log;
import com.opendatahub.timeseries.bdp.writer.dal.util.QueryBuilder;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Represents a Timeseries.
 * A timeseries is a set of timestamp/value tuples (measurements) with a common
 * type and period, associated to a station
 */
@Entity
@Table(name = "timeseries", indexes = {
		@Index(columnList = "station_id type_id")
}, uniqueConstraints = {
		@UniqueConstraint(columnNames = { "station_id type_id period value_table" })
})
public class TimeSeries {
	@Id
	@GeneratedValue(generator = "timeseries_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "timeseries_gen", sequenceName = "timeseries_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('timeseries_seq')")
	protected Long id;

	@ManyToOne(cascade = CascadeType.ALL, optional = false)
	private Station station;

	@ManyToOne(cascade = CascadeType.PERSIST, optional = false)
	private DataType type;

	@Column(nullable = false)
	private Integer period;

	@Column(nullable = false)
	private String value_table;

	@Column(nullable = false)
	private Partition partition;

	protected TimeSeries() {
	}

	protected TimeSeries(Station station, DataType type, Integer period, String valueTable) {
		this.station = station;
		this.type = type;
		this.period = period;
		this.value_table = valueTable;
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

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getValueTable() {
		return value_table;
	}

	public void setValueTable(String valueTable) {
		this.value_table = valueTable;
	}
    private static final Logger LOG = LoggerFactory.getLogger(MeasurementAbstractHistory.class);

	/**
	 * Retrieve the date of the last inserted record of {@code table}.
	 *
	 * Hibernate does not support {@code UNION ALL} queries, hence we must retrieve
	 * all
	 * last record entries of all subclasses and compare programmatically.
	 *
	 * @param em      entity manager
	 * @param station entity {@link Station} to filter by
	 * @param type    entity {@link DataType} to filter by
	 * @param period  interval between measurements to filter by
	 * @param table   implementation of m which we need to query
	 * @return date of the last inserted record
	 */
	public static <T> Date getDateOfLastRecordImpl(EntityManager em, Station station, DataType type, Integer period,
			T table) {
		if (station == null)
			return null;

		return QueryBuilder
				.init(em)
				.addSql("SELECT record.timestamp FROM " + table.getClass().getSimpleName() + " record",
						"WHERE record.station = :station")
				.setParameterIfNotNull("type", type, "AND record.type = :type")
				.setParameterIfNotNull("period", period, "AND record.period = :period")
				.setParameter("station", station)
				.addSql("ORDER BY record.timestamp DESC")
				.buildSingleResultOrAlternative(Date.class, new Date(-1));
	}

	public static Date getDateOfLastRecordSingleImpl(EntityManager em, String stationType, String stationCode,
			String dataTypeName, Integer period) {
		if (stationType == null || stationCode == null)
			return null;

		return QueryBuilder
				.init(em)
				.nativeQuery()
				.addSql("SELECT max(timestamp) FROM {h-schema}measurement m")
				.addSql("JOIN {h-schema}station s ON s.id = m.station_id")
				.addSql("JOIN {h-schema}type t ON t.id = m.type_id")
				.addSql("WHERE stationtype = :stationtype AND stationcode = :stationcode")
				.setParameterIfNotEmpty("type", dataTypeName, "AND cname = :type")
				.setParameterIfNotNull("period", period, "AND period = :period")
				.setParameter("stationtype", stationType)
				.setParameter("stationcode", stationCode)
				.addSql("UNION ALL")
				.addSql("SELECT max(timestamp) FROM {h-schema}measurementjson m")
				.addSql("JOIN {h-schema}station s ON s.id = m.station_id")
				.addSql("JOIN {h-schema}type t ON t.id = m.type_id")
				.addSql("WHERE stationtype = :stationtype AND stationcode = :stationcode")
				.setParameterIfNotEmpty("type", dataTypeName, "AND cname = :type")
				.setParameterIfNotNull("period", period, "AND period = :period")
				.setParameter("stationtype", stationType)
				.setParameter("stationcode", stationCode)
				.addSql("UNION ALL")
				.addSql("SELECT max(timestamp) FROM {h-schema}measurementstring m")
				.addSql("JOIN {h-schema}station s ON s.id = m.station_id")
				.addSql("JOIN {h-schema}type t ON t.id = m.type_id")
				.addSql("WHERE stationtype = :stationtype AND stationcode = :stationcode")
				.setParameterIfNotEmpty("type", dataTypeName, "AND cname = :type")
				.setParameterIfNotNull("period", period, "AND period = :period")
				.setParameter("stationtype", stationType)
				.setParameter("stationcode", stationCode)
				.addSql("ORDER BY 1 DESC NULLS LAST")
				.buildSingleResultOrNull(Date.class);
	}

	/**
	 * This is the {@link findLatestEntryImpl} implementation without permission
	 * control.
	 *
	 * <p>
	 * THIS METHOD SEES ALL DATA, SO CAREFUL WHEN YOU USE IT
	 * </p>
	 *
	 * Use
	 * {@link MeasurementAbstract#findLatestEntry(EntityManager, Station, DataType, Integer)},
	 * if you need permission handling.
	 *
	 * @param em      entity manager
	 * @param station entity {@link Station} to filter by
	 * @param type    entity {@link DataType} to filter by
	 * @param period  interval between measurements to filter by
	 * @param table
	 * @return
	 */
	public static <T extends MeasurementAbstract> MeasurementAbstract findLatestEntry(EntityManager em, Station station,
			DataType type, Integer period, Class<T> subClass) {
		if (station == null)
			return null;

		return QueryBuilder
				.init(em)
				.addSql("SELECT record FROM " + subClass.getSimpleName() + " record WHERE record.station = :station")
				.setParameter("station", station)
				.setParameterIfNotNull("type", type, "AND record.type = :type")
				.setParameterIfNotNull("period", period, "AND record.period = :period")
				.addSql("ORDER BY record.timestamp DESC")
				.buildSingleResultOrNull(subClass);
	}

	/**
	 * @param em      entity manager
	 * @param station entity {@link Station} to filter by
	 * @param type    entity {@link DataType} to filter by
	 * @param period  interval between measurements to filter by
	 * @param table   measurement implementation table to search in
	 * @return newest measurement {@link MeasurementAbstract} of a specific station.
	 *         It can also be narrowed down to type and period
	 */
	protected static <T extends MeasurementAbstract> MeasurementAbstract findLatestEntryImpl(EntityManager em,
			Station station, DataType type, Integer period, T table) {
		if (station == null)
			return null;

		return QueryBuilder
				.init(em)
				.addSql("SELECT record FROM " + table.getClass().getSimpleName() + " record",
						"WHERE record.station = :station")
				.setParameterIfNotNull("period", period, "AND record.period = :period")
				.setParameterIfNotNull("type", type, "AND record.type = :type")
				.setParameter("station", station)
				.addSql("ORDER BY record.timestamp DESC")
				.buildSingleResultOrNull(table.getClass());
	}

	/**
	 * <p>
	 * persists all measurement data send to the writer from data collectors to the
	 * database.<br/>
	 * This method goes down the data tree and persists all new records<br/>
	 * it also updates the newest measurement in {@link MeasurementAbstract}, if it
	 * really is newer
	 * </p>
	 * 
	 * @param em          entity manager
	 * @param stationType typology of the specific station, e.g., MeteoStation,
	 *                    EnvironmentStation
	 * @param dataMap     container for data send from data collector containing
	 *                    measurements<br/>
	 *                    Data is received in a tree structure, containing in the
	 *                    first level the identifier of the correlated station,<br/>
	 *                    on the second level the identifier of the correlated data
	 *                    type and on the last level the data itself
	 * @throws JPAException if data is in any way corrupted or one of the references
	 *                      {@link Station}, {@link DataType}<br/>
	 *                      does not exist in the database yet
	 */
	@SuppressWarnings("unchecked")
	public static void pushRecords(EntityManager em, String stationType, DataMapDto<RecordDtoImpl> dataMap) {
		Log log = new Log(LOG, "pushRecords");
		try {
			Provenance provenance = Provenance.findByUuid(em, dataMap.getProvenance());
			if (provenance == null) {
				throw new JPAException(String.format("Provenance with UUID %s not found", dataMap.getProvenance()));
			}
			log.setProvenance(provenance);

			var skippedDataTypes = new HashSet<String>();
			int skippedCount = 0;

			for (Entry<String, DataMapDto<RecordDtoImpl>> stationEntry : dataMap.getBranch().entrySet()) {
				Station station = Station.findStation(em, stationType, stationEntry.getKey());
				if (station == null) {
					log.warn(String.format("Station '%s/%s' not found. Skipping...", stationType,
							stationEntry.getKey()));
					continue;
				}
				for (Entry<String, DataMapDto<RecordDtoImpl>> typeEntry : stationEntry.getValue().getBranch()
						.entrySet()) {
					try {
						DataType type = DataType.findByCname(em, typeEntry.getKey());
						if (type == null) {
							log.warn(String.format("Type '%s' not found. Skipping...", typeEntry.getKey()));
							continue;
						}
						List<? extends RecordDtoImpl> dataRecords = typeEntry.getValue().getData();
						if (dataRecords.isEmpty()) {
							log.warn("Empty data set. Skipping...");
							continue;
						}
						dataRecords.sort((l, r) -> Long.compare(l.getTimestamp(), r.getTimestamp()));

						// Some datacollectors write multiple periods in a single call.
						// They need to be handled as if they were separate datatypes, each with their
						// own latest measurement
						Map<Integer, Period> periods = new HashMap<>();

						em.getTransaction().begin();

						for (RecordDtoImpl recordDto : dataRecords) {
							SimpleRecordDto simpleRecordDto = (SimpleRecordDto) recordDto;
							Integer periodSeconds = simpleRecordDto.getPeriod();
							if (periodSeconds == null) {
								log.error("No period specified. Skipping...");
								continue;
							}
							Period period = periods.get(periodSeconds);
							if (period == null) {
								period = new Period(em, station, type, periodSeconds, provenance);
								periods.put(periodSeconds, period);
							}

							Date dateOfMeasurement = new Date(recordDto.getTimestamp());
							Object valueObj = simpleRecordDto.getValue();

							if (valueObj instanceof Number) {
								MeasurementHistory rec = new MeasurementHistory(station, type,
										((Number) valueObj).doubleValue(),
										dateOfMeasurement, periodSeconds);
								period.number.addHistory(em, log, simpleRecordDto, rec);
							} else if (valueObj instanceof String) {
								MeasurementStringHistory rec = new MeasurementStringHistory(station, type,
										(String) valueObj,
										dateOfMeasurement, periodSeconds);
								period.string.addHistory(em, log, simpleRecordDto, rec);
							} else if (valueObj instanceof Map) {
								MeasurementJSONHistory rec = new MeasurementJSONHistory(station, type,
										(Map<String, Object>) valueObj,
										dateOfMeasurement, periodSeconds);
								period.json.addHistory(em, log, simpleRecordDto, rec);
							} else {
								log.warn(
										String.format(
												"Unsupported data format for %s/%s/%s with value '%s'. Skipping...",
												stationType,
												stationEntry.getKey(),
												typeEntry.getKey(),
												(valueObj == null ? "(null)" : valueObj.getClass().getSimpleName())));
							}
						}

						for (Period period : periods.values()) {
							period.number.updateLatest(em, (newest) -> {
								return new Measurement(station, type, ((Number) newest.getValue()).doubleValue(),
										new Date(newest.getTimestamp()), period.period);
							});
							period.string.updateLatest(em, (newest) -> {
								return new MeasurementString(station, type, (String) newest.getValue(),
										new Date(newest.getTimestamp()),
										period.period);
							});
							period.json.updateLatest(em, (newest) -> {
								return new MeasurementJSON(station, type, (Map<String, Object>) newest.getValue(),
										new Date(newest.getTimestamp()),
										period.period);
							});

							skippedDataTypes.add(type.getCname());
							skippedCount += period.skippedCount;
						}

						em.getTransaction().commit();
					} catch (Exception ex) {
						log.error(
								String.format("Exception '%s'... Skipping this measurement!", ex.getMessage()),
								ex);
						LOG.debug("Printing stack trace", ex);
					} finally {
						if (em.getTransaction().isActive()) {
							em.getTransaction().rollback();
						}
					}
				}
			}

			if (skippedCount > 0) {
				log.warn(String.format("Skipped %d records due to timestamp for type: [%s, (%s)]",
						skippedCount,
						stationType, String.join(", ", skippedDataTypes)));
			}
		} catch (Exception e) {
			throw JPAException.unnest(e);
		} finally {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			em.clear();
			if (em.isOpen())
				em.close();
		}
	}

	private static class Period {
		public Series number;
		public Series string;
		public Series json;

		private Station station;
		private DataType type;
		private Integer period;
		private Provenance provenance;
		public int skippedCount = 0;

		private class Series {
			private MeasurementAbstract latest;
			private long newestTime;
			private RecordDtoImpl newest;

			public Series(EntityManager em, Class<? extends MeasurementAbstract> clazz) {
				latest = TimeSeries.findLatestEntry(em, station, type, period, clazz);
				newestTime = (latest != null) ? latest.getTimestamp().getTime() : 0;
				newest = null;
			}

			private void updateNewest(RecordDtoImpl dto) {
				if (newest == null || newest.getTimestamp() < dto.getTimestamp()) {
					newest = dto;
					newestTime = newest.getTimestamp();
				}
			}

			public void addHistory(EntityManager em, Log log, SimpleRecordDto dto, MeasurementAbstractHistory rec) {
				// In case of duplicates within a single push, which one is written and which
				// one is discarded, is undefined (depends on the record sorting above)
				if (newestTime < dto.getTimestamp()) {
					rec.setProvenance(provenance);
					em.persist(rec);
					updateNewest(dto);
				} else {
					LOG.debug(String.format("Skipping record due to timestamp: [%s, %s, %s, %d, %d]",
							station.stationtype, station.stationcode, type.getCname(), period, dto.getTimestamp()));
					skippedCount++;
				}
			}

			public void updateLatest(EntityManager em, Function<RecordDtoImpl, MeasurementAbstract> measurementMapper) {
				if (newest != null) {
					var measurement = measurementMapper.apply(newest);
					if (latest == null) {
						measurement.setProvenance(provenance);
						em.persist(measurement);
					} else if (newest.getTimestamp() > latest.getTimestamp().getTime()) {
						latest.setTimestamp(new Date(newest.getTimestamp()));
						latest.setValue(measurement.getValue());
						latest.setProvenance(provenance);
						em.merge(latest);
					}
				}
			}
		}

		public Period(EntityManager em, Station station, DataType type, Integer period, Provenance provenance) {
			this.station = station;
			this.type = type;
			this.period = period;
			this.provenance = provenance;

			number = new Series(em, Measurement.class);
			string = new Series(em, MeasurementString.class);
			json = new Series(em, MeasurementJSON.class);
		}
	}

	private static List<RecordDto> castToDtos(List<MeasurementAbstractHistory> result, boolean setPeriod) {
		List<RecordDto> dtos = new ArrayList<>();
		for (MeasurementAbstractHistory m : result) {
			SimpleRecordDto dto = new SimpleRecordDto(m.getTimestamp().getTime(), m.getValue(),
					setPeriod ? m.getTimeseries().getPeriod() : null);
			dto.setCreated_on(m.getCreated_on().getTime());
			dtos.add(dto);
		}
		return dtos;
	}

	/**
	 * <p>
	 * the only method which requests history data from the biggest existing tables
	 * in the underlying DB,<br/>
	 * it's very important that indexes are set correctly to avoid bad performance
	 * </p>
	 * 
	 * @param em          entity manager
	 * @param typology    of the specific station, e.g., MeteoStation,
	 *                    EnvironmentStation
	 * @param identifier  unique station identifier, required
	 * @param cname       unique type identifier, required
	 * @param start       time filter start in milliseconds UTC for query, required
	 * @param end         time filter start in milliseconds UTC for query, required
	 * @param period      interval between measurements
	 * @param tableObject implementation which calls this method to decide which
	 *                    table to query, required
	 * @return a list of measurements from history tables
	 */
	protected static <T> List<RecordDto> findRecordsImpl(EntityManager em, String stationtype, String identifier,
			String cname, Date start, Date end, Integer period, T tableObject) {
		List<MeasurementAbstractHistory> result = QueryBuilder
				.init(em)
				.addSql("SELECT record")
				.addSql("FROM  " + tableObject.getClass().getSimpleName() + " record",
						"WHERE record.station = (",
						"SELECT s FROM Station s WHERE s.stationtype = :stationtype AND s.stationcode = :stationcode",
						")",
						"AND record.type = (SELECT t FROM DataType t WHERE t.cname = :cname)",
						"AND record.timestamp between :start AND :end")
				.setParameterIfNotNull("period", period, "AND record.period = :period")
				.setParameter("stationtype", stationtype)
				.setParameter("stationcode", identifier)
				.setParameter("cname", cname)
				.setParameter("start", start)
				.setParameter("end", end)
				.addSql("ORDER BY record.timestamp")
				.buildResultList(MeasurementAbstractHistory.class);
		return TimeSeries.castToDtos(result, period == null);
	}
}
