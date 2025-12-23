// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019-2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Partition represents a partition key used to partition measurement history.
 */
@Entity
@Table(name = "partition", indexes = {
		@Index(columnList = "name")
}, uniqueConstraints = {
		@UniqueConstraint(columnNames = { "name" })
})
@Cacheable
public class Partition {

	@Id
	@GeneratedValue(generator = "partition_gen", strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "partition_gen", sequenceName = "partition_seq", allocationSize = 1)
	@ColumnDefault(value = "nextval('partition_seq')")
	protected Long id;

	@Column(nullable = false)
	private String name;

	@Column
	private String description;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public static synchronized Partition getDefault(EntityManager em) {
		Partition partition = em.find(Partition.class, 1L);
		if (partition == null) {
			partition = new Partition("default", "Default Partition");
			em.persist(partition);
		}
		return partition;
	}

	public void createPartition(EntityManager em, String parentTable) {
		String ddl1 = "CREATE TABLE IF NOT EXISTS " + parentTable + "_" + id
				+ " PARTITION OF " + parentTable + " FOR VALUES IN (" + id + ")";
		em.createNativeQuery(ddl1).executeUpdate();
	}

	public Partition(){}

	public Partition(String name, String description) {
		this.name = name;
		this.description = description;
	}
}
