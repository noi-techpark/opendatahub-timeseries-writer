// Copyright © 2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Session;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendatahub.timeseries.bdp.dto.dto.DataMapDto;
import com.opendatahub.timeseries.bdp.dto.dto.RecordDtoImpl;
import com.opendatahub.timeseries.bdp.dto.dto.SimpleRecordDto;
import com.opendatahub.timeseries.bdp.writer.dal.util.JPAException;
import com.opendatahub.timeseries.bdp.writer.dal.util.Log;
import com.opendatahub.timeseries.bdp.writer.dal.util.QueryBuilder;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
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
		@Index(columnList = "station_id"),
		@Index(columnList = "type_id"),
}, uniqueConstraints = {
		@UniqueConstraint(columnNames = { "station_id", "type_id", "period", "value_table" })
})
public class TimeSeries {
	@Id
	@GeneratedValue(generator = "timeseries_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "timeseries_gen", sequenceName = "timeseries_seq", allocationSize = 1)
	protected Long id;

	@ManyToOne(optional = false)
	private Station station;

	@ManyToOne(optional = false)
	private DataType type;

	@Column(nullable = false)
	private Integer period;

	@Column(nullable = false)
	@Convert(converter = ValueTableConverter.class)
	private ValueTable value_table;

	@ManyToOne(cascade = CascadeType.ALL, optional = false)
	private Partition partition;

	public static enum ValueTable {
		NUMBER("measurement", "double_value", Measurement.class, MeasurementHistory.class),
		STRING("measurementstring", "string_value", MeasurementString.class, MeasurementStringHistory.class),
		JSON("measurementjson", "json_value", MeasurementJSON.class, MeasurementJSONHistory.class);

		public final String table;
		public final String column;
		public final Class<? extends MeasurementAbstract> latestClass;
		public final Class<? extends MeasurementAbstractHistory> historyClass;

		ValueTable(String table, String column, Class<? extends MeasurementAbstract> latestClass,
				Class<? extends MeasurementAbstractHistory> historyClass) {
			this.table = table;
			this.column = column;
			this.latestClass = latestClass;
			this.historyClass = historyClass;
		}

		public static ValueTable getByTable(String s) {
			for (ValueTable v : values()) {
				if (v.table.equals(s)) {
					return v;
				}
			}
			throw new IllegalArgumentException("Unknown value: " + s);
		}
	}

	@Converter
	public static class ValueTableConverter implements AttributeConverter<ValueTable, String> {
		@Override
		public String convertToDatabaseColumn(ValueTable vt) {
			return vt == null ? null : vt.table;
		}

		@Override
		public ValueTable convertToEntityAttribute(String dbData) {
			return ValueTable.getByTable(dbData);
		}
	}

	public TimeSeries() {
	}

	public TimeSeries(Station station, DataType type, Integer period, ValueTable valueTable) {
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

	public Partition getPartition() {
		return partition;
	}

	public void setPartition(Partition partition) {
		this.partition = partition;
	}

	public ValueTable getValueTable() {
		return value_table;
	}

	public void setValueTable(ValueTable valueTable) {
		this.value_table = valueTable;
	}

	public static TimeSeries findTimeSeries(EntityManager em, Station station, DataType dataType, Integer period,
			ValueTable valueTable) {
		return QueryBuilder
				.init(em)
				.addSql("SELECT ts FROM TimeSeries ts")
				.addSql("WHERE ts.station = :station")
				.addSql("AND ts.type = :type")
				.addSql("AND ts.value_table = :value_table")
				.setParameter("station", station)
				.setParameter("type", dataType)
				.setParameter("value_table", valueTable)
				.setParameterIfNotNull("period", period, "and ts.period = :period")
				.buildSingleResultOrNull(TimeSeries.class);
	}

	private static final Logger LOG = LoggerFactory.getLogger(TimeSeries.class);

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
				.addSql("SELECT record.timestamp FROM " + table.getClass().getSimpleName() + " record")
				.addSql("JOIN record.timeseries t")
				.addSql("WHERE t.station = :station")
				.setParameterIfNotNull("type", type, "AND t.type = :type")
				.setParameterIfNotNull("period", period, "AND t.period = :period")
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
				.addSql("JOIN {h-schema}timeseries ts ON ts.id = m.timeseries_id")
				.addSql("JOIN {h-schema}station s ON s.id = ts.station_id")
				.addSql("JOIN {h-schema}type t ON t.id = ts.type_id")
				.addSql("WHERE stationtype = :stationtype AND stationcode = :stationcode")
				.setParameterIfNotEmpty("type", dataTypeName, "AND cname = :type")
				.setParameterIfNotNull("period", period, "AND period = :period")
				.setParameter("stationtype", stationType)
				.setParameter("stationcode", stationCode)
				.addSql("UNION ALL")
				.addSql("SELECT max(timestamp) FROM {h-schema}measurementjson m")
				.addSql("JOIN {h-schema}timeseries ts ON ts.id = m.timeseries_id")
				.addSql("JOIN {h-schema}station s ON s.id = ts.station_id")
				.addSql("JOIN {h-schema}type t ON t.id = ts.type_id")
				.addSql("WHERE stationtype = :stationtype AND stationcode = :stationcode")
				.setParameterIfNotEmpty("type", dataTypeName, "AND cname = :type")
				.setParameterIfNotNull("period", period, "AND period = :period")
				.setParameter("stationtype", stationType)
				.setParameter("stationcode", stationCode)
				.addSql("UNION ALL")
				.addSql("SELECT max(timestamp) FROM {h-schema}measurementstring m")
				.addSql("JOIN {h-schema}timeseries ts ON ts.id = m.timeseries_id")
				.addSql("JOIN {h-schema}station s ON s.id = ts.station_id")
				.addSql("JOIN {h-schema}type t ON t.id = ts.type_id")
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
	public MeasurementAbstract findLatestEntry(EntityManager em) {
		return QueryBuilder
				.init(em)
				.addSql("SELECT record FROM " + value_table.latestClass.getName() + " record")
				.addSql("JOIN record.timeseries ts")
				.addSql("WHERE ts.station = :station")
				.setParameter("station", station)
				.setParameterIfNotNull("type", type, "AND ts.type = :type")
				.setParameterIfNotNull("period", period, "AND ts.period = :period")
				.addSql("ORDER BY record.timestamp DESC")
				.buildSingleResultOrNull(value_table.latestClass);
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
				.addSql("SELECT record FROM " + table.getClass().getSimpleName() + " record")
				.addSql("JOIN record.timeseries ts")
				.addSql("WHERE ts.station = :station")
				.setParameterIfNotNull("period", period, "AND ts.period = :period")
				.setParameterIfNotNull("type", type, "AND ts.type = :type")
				.setParameter("station", station)
				.addSql("ORDER BY record.timestamp DESC")
				.buildSingleResultOrNull(table.getClass());
	}

	/* A record, but wrapped deliciously */
	private static class RecordBurrito extends SimpleRecordDto {
		private ValueTable table;

		public RecordBurrito(SimpleRecordDto dto) {
			super(dto.getTimestamp(), dto.getValue(), dto.getPeriod(), dto.getCreated_on());

			Object valueObj = dto.getValue();
			if (valueObj instanceof Number) {
				table = ValueTable.NUMBER;
			} else if (valueObj instanceof String) {
				table = ValueTable.STRING;
			} else if (valueObj instanceof Map) {
				table = ValueTable.JSON;
			}
		}

		public ValueTable getTable() {
			return table;
		}
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
	public static void pushRecords(EntityManager em, String stationType, DataMapDto<RecordDtoImpl> dataMap) {
		Log log = new Log(LOG, "pushRecords");
		try {
			Provenance provenance = Provenance.findByUuid(em, dataMap.getProvenance());
			if (provenance == null) {
				throw new JPAException(String.format("Provenance with UUID %s not found", dataMap.getProvenance()));
			}
			log.setProvenance(provenance);

			LOG.debug("Loading stations");
			// Preload all the stations, types, latest etc. so we don't have to query for every record
			var stations = Station.findStationsByCodes(em, stationType, dataMap.getBranch().keySet())
				.stream()
				.collect(Collectors.toMap(Station::getStationcode, Function.identity()));

			var typeNames = dataMap.getBranch().values()
				.stream()
				.flatMap(s -> s.getBranch().keySet().stream())
				.distinct()
				.collect(Collectors.toSet());
			
			LOG.debug("Loading types");
			var types = DataType.findByCnames(em, typeNames)
				.stream()
				.collect(Collectors.toMap(DataType::getCname, Function.identity()));
			
			LOG.debug("Loading latest");
			// tree stationcode/type.cname/period/table
			var measurements = MeasurementAbstract.findLatest(em, stations.values(), types.values())
				.stream()
				.collect(Collectors.groupingBy(
					l -> l.getTimeseries().station.stationcode,
					Collectors.groupingBy(
						l -> l.getTimeseries().getType().getCname(),
						Collectors.groupingBy(
							l -> l.getTimeseries().getPeriod(),
							Collectors.toMap(
								l -> l.getTimeseries().getValueTable(),
								Function.identity()
							)
						)
					)
				));

			var skippedDataTypes = new HashSet<String>();
			int skippedCount = 0;
			List<Series> allSeries = new ArrayList<>();
			
			LOG.debug("Loaded all stations, types, latest. Now walking tree");

			for (var stationBranch : dataMap.getBranch().entrySet()) {
				Station station = stations.get((String)stationBranch.getKey());
				if (station == null) {
					log.warn(String.format("Station '%s/%s' not found. Skipping...", stationType,
							stationBranch.getKey()));
					continue;
				}
				for (var typeBranch : stationBranch.getValue().getBranch().entrySet()) {
					DataType type = types.get(typeBranch.getKey());
					if (type == null) {
						log.warn(String.format("Type '%s' not found. Skipping...", typeBranch.getKey()));
						continue;
					}
					var dataRecords = typeBranch.getValue().getData();
					if (dataRecords.isEmpty()) {
						log.warn("Empty data set. Skipping...");
						continue;
					}

					// group records by datatype / period and sort by timestamp
					// grouping to handle mixed value types (e.g. string/double) and periods within
					// the same type
					// timestamp sort to discard duplicate timestamps (because we compare against
					// the running latest)
					List<RecordBurrito> simpleRecords = dataRecords.stream()
							.map((r) -> new RecordBurrito((SimpleRecordDto) r))
							.peek(r -> {
								if (r.getTable() == null || r.getPeriod() == null || r.getTimestamp() == null){
									throw new IllegalStateException("Null field or invalid data type in Record: " + r);
								}
							})
							.sorted(Comparator.comparing(RecordBurrito::getTable)
									.thenComparing(RecordBurrito::getPeriod)
									.thenComparing(RecordBurrito::getTimestamp))
							.toList();

					Series series = new Series(em, provenance, station, type, null, null);

					for (RecordBurrito record : simpleRecords) {
						// Since we've sorted before looping, if a record doesn't fit the current timeseries, we move on to the next one
						if (!series.fits(station, type, record.getPeriod(), record.getTable())) {
							// check if latest record (and timeseries) exists, or we create a new one
							MeasurementAbstract latest = Optional.ofNullable(measurements)
									.map(m -> m.get(station.stationcode))
									.map(m -> m.get(type.getCname()))
									.map(m -> m.get(record.getPeriod()))
									.map(m -> m.get(record.getTable()))
									.orElse(null);
							if (latest != null) {
								series = new Series(provenance, latest);
							} else {
								series = new Series(em, provenance, station, type, record.getPeriod(), record.getTable());
							}
							if (record.getTable() != null) {
								allSeries.add(series);
							}
						}

						series.addHistory(record);

						if (series.skippedCount > 0) {
							skippedDataTypes.add(type.getCname());
							skippedCount += series.skippedCount;
						}
					}
				}
			}

			if (skippedCount > 0) {
				log.warn(String.format("Skipped %d records due to timestamp for type: [%s, (%s)]", skippedCount,
						stationType, String.join(", ", skippedDataTypes)));
			}
			
			// Performance optimizations to leverage hibernate batch operations
			// For that we do all the inserts and updates grouped
			
			// Sort the timeseries per table, because hibernate only batches per table
			allSeries = allSeries.stream()
				.filter(s -> !s.getMeasures().isEmpty())
				.sorted((l, r) -> l.getTable().compareTo(r.getTable())).toList();
			
			LOG.debug("Starting insert");

			em.getTransaction().begin();

			allSeries.stream()
				.map(s -> s.timeseries)
				.filter(t -> t.id == null)
				.forEach(t -> em.persist(t));
			
			allSeries.stream()
				.flatMap(s -> s.measures.stream())
				.forEach(m -> em.persist(m));
			
			LOG.debug("updating latest");
			for (Series s : allSeries) {
				s.updateLatest(em);
			}
			LOG.debug("committing");

			em.getTransaction().commit();
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

	private MeasurementAbstractHistory newHistoryRecord(Object value, Date timestamp) {
		try {
			MeasurementAbstractHistory rec = value_table.historyClass.getDeclaredConstructor().newInstance();
			rec.setTimeseries(this);
			rec.setPartition(partition);
			rec.setValue(value);
			rec.setTimestamp(timestamp);
			return rec;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private MeasurementAbstract newLatestRecord(Object value, Date timestamp) {
		try {
			MeasurementAbstract rec = value_table.latestClass.getDeclaredConstructor().newInstance();
			rec.setTimeseries(this);
			rec.setValue(value);
			rec.setTimestamp(timestamp);
			return rec;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static class Series {
		public int skippedCount = 0;

		private MeasurementAbstract latest;
		private long newestTime;
		private RecordDtoImpl newest;
		private TimeSeries timeseries;
		private Provenance provenance;
		
		public String getTable(){
			return timeseries.value_table.name();
		}

		public boolean fits(Station station, DataType type, Integer period, ValueTable table) {
			return station.getId().equals(timeseries.station.getId()) 
				&& type.getId().equals(timeseries.getType().getId()) 
				&& period.equals(timeseries.period)
				&& table.equals(timeseries.getValueTable());
		}

		public Series(Provenance provenance, MeasurementAbstract latest) {
			this.provenance = provenance;
			timeseries = latest.getTimeseries();
			this.latest = latest;
			newestTime = latest.getTimestamp().getTime();
			newest = null;
		}

		public Series(EntityManager em, Provenance provenance, Station station, DataType type, Integer period, ValueTable table) {
			this.provenance = provenance;
			timeseries = new TimeSeries(station, type, period, table);
			timeseries.setPartition(Partition.getDefault(em));
		}

		private void updateNewest(RecordDtoImpl dto) {
			if (newest == null || newest.getTimestamp() < dto.getTimestamp()) {
				newest = dto;
				newestTime = newest.getTimestamp();
			}
		}
		
		private List<MeasurementAbstractHistory> measures = new ArrayList<>();

		public void addHistory(SimpleRecordDto dto) throws Exception {
			// In case of duplicates within a single push, which one is written and which
			// one is discarded, is undefined (depends on the record sorting above)
			if (newestTime >= dto.getTimestamp()) {
				LOG.debug(String.format("Skipping record due to timestamp: [%s, %s, %s, %d, %d]",
						timeseries.station.stationtype, timeseries.station.stationcode, timeseries.type.getCname(),
						timeseries.period, dto.getTimestamp()));
				skippedCount++;
			} else if (timeseries.getValueTable() == null) {
				LOG.debug(String.format("Skipping record due to unknown value type: [%s, %s, %s, %d, %d, %s]",
						timeseries.station.stationtype, timeseries.station.stationcode, timeseries.type.getCname(),
						timeseries.period, dto.getTimestamp(), dto.getValue()));
				skippedCount++;
			} else {
				MeasurementAbstractHistory rec = timeseries.newHistoryRecord(dto.getValue(),
						new Date(dto.getTimestamp()));
				rec.setProvenance(provenance);
				measures.add(rec);
				updateNewest(dto);
			}
		}
		
		public List<MeasurementAbstractHistory> getMeasures(){
			return measures;
		}

		private void updateLatest(EntityManager em) {
			if (newest != null) {
				if (latest == null) {
					var measurement = timeseries.newLatestRecord(newest.getValue(), new Date(newest.getTimestamp()));
					measurement.setProvenance(provenance);
					em.persist(measurement);
				} else if (newest.getTimestamp() > latest.getTimestamp().getTime()) {
					latest.setTimestamp(new Date(newest.getTimestamp()));
					latest.setValue(newest.getValue());
					latest.setProvenance(provenance);
					em.merge(latest);
				}
			}
		}
	}
}
