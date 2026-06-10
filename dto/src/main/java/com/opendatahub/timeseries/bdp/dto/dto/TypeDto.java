// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.dto.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;


// @Deprecated This one is deprecated, but since we might change more than just this in the future, lets remove
// this tag and reduce warnings for now!
public class TypeDto implements Serializable {

	private static final long serialVersionUID = -1224947780318447560L;
	private String id;
	private String unit;
	private Map<String,String> desc = new HashMap<>();
	private String typeOfMeasurement;
	private Set<Integer> acquisitionIntervals = new TreeSet<>();

	public TypeDto() {
	}
	public TypeDto(String id, Integer interval) {
		this.id = id;
		if (interval!= null)
			this.acquisitionIntervals.add(interval);
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getUnit() {
		return unit;
	}
	public void setUnit(String unit) {
		this.unit = (unit == null || unit.isEmpty()) ? null : unit;
	}
	public Map<String, String> getDesc() {
		return desc;
	}
	public void setDesc(Map<String, String> desc) {
		this.desc = desc;
	}
	public String getTypeOfMeasurement() {
		return typeOfMeasurement;
	}
	public void setTypeOfMeasurement(String typeOfMeasurement) {
		this.typeOfMeasurement = (typeOfMeasurement == null || typeOfMeasurement.isEmpty()) ? null : typeOfMeasurement;
	}
	public Set<Integer> getAcquisitionIntervals() {
		return acquisitionIntervals;
	}
	public void setAcquisitionIntervals(Set<Integer> acquisitionIntervals) {
		this.acquisitionIntervals = acquisitionIntervals;
	}

	private static boolean equal(Object a, Object b) {
		if (a == null && b == null)
			return true;
		return (a != null && b != null && a.equals(b));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (!(obj instanceof TypeDto)) {
			return false;
		}
		TypeDto dto = (TypeDto) obj;
		return equal(id, dto.getId()) && equal(unit, dto.getUnit());
	}

	@Override
	public int hashCode() {
		return 0;
	}

	@Override
	public String toString() {
		return "TypeDto [id=" + id + ", unit=" + unit + ", desc=" + desc + ", typeOfMeasurement=" + typeOfMeasurement + ", acquisitionIntervals=" + acquisitionIntervals + "]";
	}
}
