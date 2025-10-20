// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019-2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.opendatahub.timeseries.bdp.dto.dto.RecordDto;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * <p>
 * This entity contains all measurements and is the biggest container for the
 * data.
 * Each measurement <strong>must</strong> extend this base class to keep
 * integrity.
 * It contains the two most important references to station and type and also
 * contains generic
 * methods on how data gets stored and retrieved.
 *
 * @author Peter Moser
 * @author Patrick Bertolla
 * @author Clemens Zagler
 */
@MappedSuperclass
public abstract class MeasurementAbstractHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(nullable = false)
    private Date created_on;

    @Column(nullable = false)
    private Date timestamp;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    private Provenance provenance;

	@ManyToOne(cascade = CascadeType.ALL, optional = false)
	private TimeSeries timeseries;

	@Column(nullable = false )
	private String partition_id;

    public abstract List<RecordDto> findRecords(EntityManager em, String stationtype, String identifier, String cname,
            Date start, Date end, Integer period);

    protected MeasurementAbstractHistory() {
        this.created_on = new Date();
    }

    protected MeasurementAbstractHistory(TimeSeries timeseries, Date timestamp) {
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

    public String getPartition_id() {
        return partition_id;
    }

    public void setPartition_id(String partition_id) {
        this.partition_id = partition_id;
    }

    public abstract void setValue(Object value);

    public abstract Object getValue();
}
