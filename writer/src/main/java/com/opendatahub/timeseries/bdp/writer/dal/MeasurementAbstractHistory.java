// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019-2025 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
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
@IdClass(MeasurementAbstractHistory.MeasurementHistoryId.class)
public abstract class MeasurementAbstractHistory implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "timestamp", nullable = false)
    private Date timestamp;

    @Id
	@ManyToOne(cascade = CascadeType.PERSIST, optional = false)
    @JoinColumn(name = "timeseries_id")
    private TimeSeries timeseries;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    private Provenance provenance;

    @Column(nullable = false)
    private Date created_on;

    @ManyToOne(optional = false)
    private Partition partition;

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

    public Partition getPartition() {
        return partition;
    }

    public void setPartition(Partition partition) {
        this.partition = partition;
    }

    public abstract void setValue(Object value);

    public abstract Object getValue();

    /** History records don't have an ID, but they are unique for each timeseries_id and timestamp, so we use that as composite for JPA */
    public static class MeasurementHistoryId implements Serializable {
        private static final long serialVersionUID = 1L;

        public Long timeseries; // Must match field name and be the ID type of TimeSeries
        public Date timestamp;
        
        public MeasurementHistoryId(){
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MeasurementHistoryId)) return false;
            MeasurementHistoryId that = (MeasurementHistoryId) o;
            return Objects.equals(timeseries, that.timeseries)
                && Objects.equals(timestamp, that.timestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(timeseries, timestamp);
        }
    }
}
