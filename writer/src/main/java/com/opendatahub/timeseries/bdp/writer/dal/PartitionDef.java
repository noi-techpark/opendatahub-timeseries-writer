// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019-2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import java.util.List;

import org.hibernate.annotations.ColumnDefault;

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
 * PartitionDef defines rules on which timeseries are stored in which partition
 */
@Entity
@Table(name = "partition_def", indexes = {
		@Index(columnList = "origin"),
		@Index(columnList = "stationtype"),
		@Index(columnList = "type_id"),
		@Index(columnList = "period"),
}, uniqueConstraints = {
		@UniqueConstraint(columnNames = { "origin", "stationtype", "type_id", "period" })
})
public class PartitionDef {
	@Id
	@GeneratedValue(generator = "partition_def_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "partition_def_gen", sequenceName = "partition_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('partition_def_seq')")
	public Long id;

	@ManyToOne(cascade = CascadeType.PERSIST, optional = false)
	public Partition partition;

	@Column
	public String origin;

	@Column
	public String stationtype;

	@ManyToOne(cascade = CascadeType.PERSIST)
	public DataType type;

	@Column
	public Integer period;
	
	
	public PartitionDef(){}

	public PartitionDef(Partition partition, String origin, String stationtype, DataType type, Integer period) {
		this.partition = partition;
		this.origin = origin;
		this.stationtype = stationtype;
		this.type = type;
		this.period = period;
	}

	public static Partition findPartition(EntityManager em, String origin, String stationType, DataType type, Integer period) {
		String jpql = """
				WITH scored AS (
				    SELECT pd.partition as partition,
				           (CASE WHEN pd.origin = :origin THEN 1 ELSE 0 END +
				            CASE WHEN pd.stationtype = :stationType THEN 1 ELSE 0 END +
				            CASE WHEN pd.type.id = :typeId THEN 1 ELSE 0 END +
				            CASE WHEN pd.period = :period THEN 1 ELSE 0 END) as score
				    FROM PartitionDef pd
				    WHERE (pd.origin = :origin OR pd.origin IS NULL)
				      AND (pd.stationtype = :stationType OR pd.stationtype IS NULL)
				      AND (pd.type.id = :typeId OR pd.type.id IS NULL)
				      AND (pd.period = :period OR pd.period IS NULL)
				)
				SELECT s.partition
				FROM scored s
				WHERE s.score = (SELECT MAX(s2.score) FROM scored s2)
				""";

		List<Partition> results = em.createQuery(jpql, Partition.class)
				.setParameter("origin", origin)
				.setParameter("stationType", stationType)
				.setParameter("typeId", type.getId())
				.setParameter("period", period)
				.getResultList();

		if (results.isEmpty()) {
			return null;
		}

		if (results.size() > 1) {
			throw new IllegalStateException("Multiple partitions found with same match score");
		}

		return results.get(0);
	}
}
