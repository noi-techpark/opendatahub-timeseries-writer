// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.writer;

import java.net.URI;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.opendatahub.timeseries.bdp.dto.dto.DataMapDto;
import com.opendatahub.timeseries.bdp.dto.dto.DataTypeDto;
import com.opendatahub.timeseries.bdp.dto.dto.ProvenanceDto;
import com.opendatahub.timeseries.bdp.dto.dto.RecordDtoImpl;
import com.opendatahub.timeseries.bdp.dto.dto.StationDto;
import com.opendatahub.timeseries.bdp.writer.dal.util.JPAException;
import com.opendatahub.timeseries.bdp.writer.writer.authz.AuthorizeSyncStation;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Spring controller handling JSON requests to the writer API.
 * For more documentation on the single API calls refer to {@link DataManager}
 *
 * @author Patrick Bertolla
 * @author Peter Moser
 */
@RestController
@RequestMapping("/json")
public class JsonController {

	@Autowired
	DataManager dataManager;

	@PostMapping(value = "/provenance")
	@ResponseBody
	public ResponseEntity<String> createProvenance(
		HttpServletRequest request,
		@RequestBody ProvenanceDto provenance,
		@RequestParam(value = "prn", required = false) String provenanceName,
		@RequestParam(value = "prv", required = false) String provenanceVersion
	) {
		return ResponseEntity.ok(dataManager.addProvenance(provenance));
	}

	@GetMapping(value = "/provenance")
	@ResponseBody
	public List<ProvenanceDto> getProvenance(
		HttpServletRequest request,
		@RequestParam(value = "uuid", required = false) String uuid,
		@RequestParam(value = "dataCollector", required = false) String name,
		@RequestParam(value = "dataCollectorVersion", required = false) String version,
		@RequestParam(value = "lineage", required = false) String lineage,
		@RequestParam(value = "prn", required = false) String provenanceName,
		@RequestParam(value = "prv", required = false) String provenanceVersion
	) {
		return dataManager.findProvenance(uuid, name, version, lineage);
	}


	@GetMapping(value = "/getDateOfLastRecord")
	@ResponseBody
	public ResponseEntity<Date> dateOfLastRecordMissingTopology() {
		throw new JPAException("Missing station type. For example set MyStationType: .../getDateOfLastRecord/MyStationType?stationId=X&typeId=Y");
	}

	@GetMapping(value = "/getDateOfLastRecord/{stationType}")
	@ResponseBody
	public ResponseEntity<Date> dateOfLastRecord(
		HttpServletRequest request,
		@PathVariable("stationType") String stationType,
		@RequestParam("stationId") String stationId,
		@RequestParam(value = "typeId", required = false) String typeId,
		@RequestParam(value = "period", required = false) Integer period,
		@RequestParam(value = "prn", required = false) String provenanceName,
		@RequestParam(value = "prv", required = false) String provenanceVersion
	) {
		return ResponseEntity.ok(dataManager.getDateOfLastRecord(stationType, stationId, typeId, period));
	}

	@GetMapping(value = "/stations/{stationType}", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	// This actually returns a List<StationDto> format wise, but for optimization purposes the whole JSON is built during SQL query already.
	public Object stationsGetList(
		HttpServletRequest request,
		@PathVariable("stationType") String stationType,
		@RequestParam(value = "origin", required = false) String origin,
		@RequestParam(value = "prn", required = false) String provenanceName,
		@RequestParam(value = "prv", required = false) String provenanceVersion
	) {
		return dataManager.getStationsNative(stationType, origin);
	}

	@GetMapping(value = "/stations")
	@ResponseBody
	public List<String> stationsGetTypes(
		HttpServletRequest request,
		@RequestParam(value = "prn", required = false) String provenanceName,
		@RequestParam(value = "prv", required = false) String provenanceVersion
	) {
		return dataManager.getStationTypes();
	}

	@GetMapping(value = "/types")
	@ResponseBody
	public List<String> dataTypes(
		HttpServletRequest request,
		@RequestParam(value = "prn", required = false) String provenanceName,
		@RequestParam(value = "prv", required = false) String provenanceVersion
	) {
		return dataManager.getDataTypes();
	}

	@PostMapping(value = "/pushRecords")
	@ResponseBody
	public ResponseEntity<Object> pushRecordsMissingTopology() {
		throw new JPAException("Missing station type. For example set MyStationType: .../pushRecords/MyStationType");
	}

	@PostMapping(value = "/pushRecords/{stationType}")
	@ResponseBody
	public ResponseEntity<Object> pushRecords(
		HttpServletRequest request,
		@RequestBody(required = true) DataMapDto<RecordDtoImpl> dataMap,
		@PathVariable String stationType,
		@RequestParam(value = "prn", required = false) String provenanceName,
		@RequestParam(value = "prv", required = false) String provenanceVersion
	) {
		return dataManager.pushRecords(stationType, null, dataMap);
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@PostMapping(value = "/patchStations")
	@ResponseBody
	public void patchStations(
		HttpServletRequest request,
		@RequestBody(required = true) List<StationDto> stations,
		@RequestParam(value = "prn", required = false) String provenanceName,
		@RequestParam(value = "prv", required = false) String provenanceVersion
	) {
		dataManager.patchStations(stations);
	}

	@PostMapping(value = "/syncStations")
	@ResponseBody
	public ResponseEntity<Object> syncStationsMissingTopology() {
		throw new JPAException("Missing station type. For example set MyStationType: .../syncStations/MyStationType");
	}

	@PostMapping(value = "/syncStations/{stationType}")
	@ResponseBody
	public ResponseEntity<Object> syncStations(
		HttpServletRequest request,
		@PathVariable String stationType,
		@RequestBody(required = true) List<StationDto> stationDtos,
		@RequestParam(value = "prn", required = false) String provenanceName,
		@RequestParam(value = "prv", required = false) String provenanceVersion,
		@RequestParam(value = "syncState", required = false, defaultValue = "true") Boolean syncState,
		@RequestParam(value = "onlyActivation", required = false, defaultValue = "false") Boolean onlyActivation
	) {
		AuthorizeSyncStation.authorize(request, stationType, stationDtos, syncState, onlyActivation);
		return dataManager.syncStations(
			stationType,
			stationDtos,
			getURIMapping("/stations/{stationType}", stationType),
			provenanceName,
			provenanceVersion,
			syncState,
			onlyActivation
		);
	}

	@PostMapping(value = {"/syncStationStates/{stationType}", "/syncStationStates/{stationType}/{origin}"})
	@ResponseBody
	public ResponseEntity<Object> syncStationStates(
		HttpServletRequest request,
		@PathVariable String stationType,
		@PathVariable(required = false) String origin,
		@RequestBody(required = true) List<String> stationCodes,
		@RequestParam(value = "prn", required = false) String provenanceName,
		@RequestParam(value = "prv", required = false) String provenanceVersion,
		@RequestParam(value = "onlyActivation", required = false, defaultValue = "false") Boolean onlyActivation
	) {
		return dataManager.syncStationStates(
			stationType,
			origin,
			stationCodes,
			getURIMapping("/stations/{stationType}/{origin}", stationType, origin),
			provenanceName,
			provenanceVersion,
			onlyActivation
		);
	}

	@PostMapping(value = "/syncDataTypes")
	@ResponseBody
	public ResponseEntity<Object> syncDataTypes(
		HttpServletRequest request,
		@RequestBody(required = true) List<DataTypeDto> data,
		@RequestParam(value = "prn", required = false) String provenanceName,
		@RequestParam(value = "prv", required = false) String provenanceVersion
	) {
		return dataManager.syncDataTypes(data, getURIMapping("/types"));
	}

	private URI getURIMapping(String mapping, Object... uriVariableValues) {
		if (mapping == null)
			mapping = "";
		else if (mapping.length() > 0)
			mapping = "/" + mapping;
		String mappingController = this.getClass().getAnnotation(RequestMapping.class).value()[0];
		return ServletUriComponentsBuilder
			.fromCurrentContextPath()
			.path(mappingController + mapping)
			.buildAndExpand(uriVariableValues)
			.toUri();
	}

}
