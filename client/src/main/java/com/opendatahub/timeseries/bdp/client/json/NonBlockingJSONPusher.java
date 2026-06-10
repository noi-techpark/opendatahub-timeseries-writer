// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.client.json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import com.opendatahub.timeseries.bdp.client.DataPusher;
import com.opendatahub.timeseries.bdp.dto.dto.DataMapDto;
import com.opendatahub.timeseries.bdp.dto.dto.DataTypeDto;
import com.opendatahub.timeseries.bdp.dto.dto.ProvenanceDto;
import com.opendatahub.timeseries.bdp.dto.dto.RecordDtoImpl;
import com.opendatahub.timeseries.bdp.dto.dto.StationDto;
import com.opendatahub.timeseries.bdp.dto.dto.StationList;
import com.opendatahub.timeseries.bdp.dto.utils.Utils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import reactor.core.publisher.Mono;

import static net.logstash.logback.argument.StructuredArguments.v;

/**
 * Send data as JSON-format to the writer. Implementation with spring REST
 * template.
 *
 * @author Patrick Bertolla
 *
 */
@Component
public abstract class NonBlockingJSONPusher extends DataPusher {
    private static final String SYNC_DATA_TYPES = "/syncDataTypes/";
    private static final String SYNC_STATIONS = "/syncStations/";
	private static final String SYNC_STATION_STATES = "syncStationStates";
    private static final String PUSH_RECORDS = "/pushRecords/";
    private static final String GET_DATE_OF_LAST_RECORD = "/getDateOfLastRecord/";
    private static final String STATIONS = "/stations/";
    private static final String PROVENANCE = "/provenance/";

	private static final Logger LOG = LoggerFactory.getLogger(NonBlockingJSONPusher.class);

	@Autowired
    @Qualifier("oauthWebClient")
    protected WebClient client;

    @Override
    @PostConstruct
    public void init() {
        super.init();
    }

    @Override
    public Object pushData(String stationType, DataMapDto<? extends RecordDtoImpl> dto) {
		LOG.info(
			"NonBlockingJSONPusher/pushData",
			v("provenance", provenance)
		);
        this.pushProvenance();
        dto.setProvenance(this.provenance.getUuid());
        return client
			.post()
			.uri(uriBuilder -> uriBuilder
				.path(PUSH_RECORDS + stationType)
				.queryParams(createParams())
				.build()
			)
			.body(Mono.just(dto), Object.class)
			.retrieve()
            .bodyToMono(Object.class)
			.block();
    }

    private void pushProvenance() {
		// We know that the provenance exist, and which UUID it has.
		// So we do not need to get that information again from the DB
		// This approach assumes, that the DB will not change from any
		// other caller.
		if (this.provenance.getUuid() != null) {
			return;
		}
		LOG.info(
			"NonBlockingJSONPusher/pushProvenance",
			v("provenance", provenance)
		);
        String provenanceUuid = client
			.post()
			.uri(uriBuilder -> uriBuilder
				.path(PROVENANCE)
				.queryParams(createParams())
				.build()
			)
			.body(Mono.just(this.provenance), ProvenanceDto.class)
			.retrieve()
            .bodyToMono(String.class)
			.block();
        this.provenance.setUuid(provenanceUuid);
    }

    public Object pushData(DataMapDto<? extends RecordDtoImpl> dto) {
        dto.clean();
        return pushData(this.stationType, dto);
    }

	/** syncStations with stationType */
	@Override
	public Object syncStations(String stationType, StationList stations) {
		return syncStationSingleChunk(stationType, stations, true, false);
	}
	@Override
	public List<Object> syncStations(String stationType, StationList stations, int chunkSize) {
		return syncStationMultiChunk(stationType, stations, chunkSize, true, false);
	}
	public List<Object> syncStations(String stationType, StationList stations, boolean syncState, boolean onlyActivation) {
		return syncStationMultiChunk(stationType, stations, STATION_CHUNK_SIZE, syncState, onlyActivation);
	}
	@Override
	public List<Object> syncStations(String stationType, StationList stations, int chunkSize, boolean syncState, boolean onlyActivation) {
		return syncStationMultiChunk(stationType, stations, chunkSize, syncState, onlyActivation);
	}

	/** syncStations without stationType */
    public Object syncStations(StationList stations) {
        return syncStationMultiChunk(this.stationType, stations, STATION_CHUNK_SIZE, true, false);
    }
    public Object syncStations(StationList stations, int chunkSize) {
        return syncStationMultiChunk(this.stationType, stations, chunkSize, true, false);
    }
	public Object syncStations(StationList stations, boolean syncState, boolean onlyActivation) {
		return syncStationMultiChunk(this.stationType, stations, STATION_CHUNK_SIZE, syncState, onlyActivation);
    }
	public Object syncStations(StationList stations, int chunkSize, boolean syncState, boolean onlyActivation) {
		return syncStationMultiChunk(this.stationType, stations, chunkSize, syncState, onlyActivation);
	}

    private Object syncStationSingleChunk(String stationType, StationList stations, boolean syncState, boolean onlyActivation) {
		LOG.info(
			"NonBlockingJSONPusher/syncStations",
			v("parameters",
				Utils.mapOf("stationType", stationType )
			),
			v("provenance", provenance)
		);
		if (stations == null || stations.isEmpty()) {
			LOG.warn("NonBlockingJSONPusher/syncStation: No stations given. Returning!");
			return null;
		}
		LOG.info(
			"NonBlockingJSONPusher/syncStation: Pushing {} stations to the writer",
			stations.size()
		);

		this.pushProvenance();

        return client
			.post()
			.uri(uriBuilder -> uriBuilder
				.path(SYNC_STATIONS + stationType)
				.queryParams(
					createParams(
						"syncState", syncState,
						"onlyActivation", onlyActivation
					)
				)
				.build()
			)
			.body(Mono.just(stations), Object.class)
			.retrieve()
            .bodyToMono(Object.class)
			.block();
    }

	private List<Object> syncStationMultiChunk(String stationType, StationList stations, int chunkSize, boolean syncState, boolean onlyActivation) {
		LOG.info(
			"NonBlockingJSONPusher/syncStations",
			v("parameters",
				Utils.mapOf(
					"stationType", stationType,
					"chunkSize", chunkSize
				)
			),
			v("provenance", provenance)
		);
		if (stations == null || stations.isEmpty()) {
			LOG.warn("NonBlockingJSONPusher/syncStation: No stations given. Returning!");
			return null;
		}

		/* Syncronize station states, that is, set the active flag to true or false */
		if (syncState) {
			List<String> stationCodes = new ArrayList<>();
			for (StationDto station : stations) {
				if (stationCodes.contains(station.getId()))
					continue;
				stationCodes.add(station.getId());
			}
			syncStationStates(stationType, stations.get(0).getOrigin(), stationCodes, onlyActivation);
		}

		if (chunkSize <= 0)
			chunkSize = STATION_CHUNK_SIZE;
		int chunks = (int) Math.ceil((float) stations.size() / chunkSize);
		LOG.info(
			"NonBlockingJSONPusher/syncStation: Syncing {} stations in {} chunks of a maximum size {}!",
			stations.size(),
			chunks,
			STATION_CHUNK_SIZE
		);

		List<Object> results = new ArrayList<>();
		for (int i = 0; i < chunks; i++) {
			// We have the following interval boundaries for subList: [from, to)
			int from = STATION_CHUNK_SIZE * i;
			int to = from + STATION_CHUNK_SIZE;
			if (to > stations.size())
				to = stations.size();
			StationList stationChunk = stations.subList(from, to);

			if (stationChunk == null || stationChunk.isEmpty()) {
				LOG.warn(
					"NonBlockingJSONPusher/syncStation: No stations in chunk {} of {}. Skipping!",
					i+1,
					chunks
				);
				continue;
			}
			LOG.info("NonBlockingJSONPusher/syncStation: Chunk {} of {}", i+1, chunks);

			// Do not sync states, since we need to do this once for all and not for each chunk
			// otherwise the last chunk would set all other stations for that station type to inactive.
			results.add(syncStationSingleChunk(stationType, stationChunk, false, onlyActivation));
		}
		LOG.info("NonBlockingJSONPusher/syncStation: READY!");

		return results;
	}

	public Object syncStationStates(
		String stationType,
		String origin,
		List<String> stationCodes,
		boolean onlyActivation
	) {
		LOG.info(
			"NonBlockingJSONPusher/syncStationStates",
			v("parameters",
				Utils.mapOf(
					"stationType", stationType,
					"origin", origin
				)
			),
			v("provenance", provenance)
		);
		if (stationCodes == null || stationCodes.isEmpty()) {
			LOG.warn("NonBlockingJSONPusher/syncStationStates: No station codes given. Returning!");
			return null;
		}
		LOG.info(
			"NonBlockingJSONPusher/syncStationStates: Syncronizing {} station states (active flag)",
			stationCodes.size()
		);

		this.pushProvenance();

        return client
			.post()
			.uri(uriBuilder -> uriBuilder
				.pathSegment(SYNC_STATION_STATES, stationType, origin)
				.queryParams(
					createParams(
						"onlyActivation", onlyActivation
					)
				)
				.build()
			)
			.body(Mono.just(stationCodes), Object.class)
			.retrieve()
            .bodyToMono(Object.class)
			.block();
	}


    @Override
    public Object syncDataTypes(String stationType, List<DataTypeDto> data) {
		LOG.info(
			"NonBlockingJSONPusher/syncDataTypes",
			v("parameters", Utils.mapOf(
					"stationType", stationType
				)
			),
			v("provenance", provenance)
		);
		if (data == null) {
			LOG.warn("NonBlockingJSONPusher/syncDataTypes: No data types given. Returning!");
			return null;
		}
		LOG.info(
			"NonBlockingJSONPusher/syncDataTypes: Pushing {} data types to the writer",
			data.size()
		);

		this.pushProvenance();

        return client
			.post()
			.uri(uriBuilder -> uriBuilder
				.path(SYNC_DATA_TYPES)
				.queryParams(createParams())
				.build()
			)
			.body(Mono.just(data), Object.class)
			.retrieve()
            .bodyToMono(Object.class)
			.block();
    }

    public Object syncDataTypes(List<DataTypeDto> data) {
        return syncDataTypes(this.stationType, data);
    }

    @Override
    public Object getDateOfLastRecord(String stationCode, String dataType, Integer period) {
		LOG.info(
			"NonBlockingJSONPusher/getDateOfLastRecord",
			v("parameters", Utils.mapOf(
					"stationCode", stationCode,
					"dataType", dataType,
					"period", period
				)
			),
			v("provenance", provenance)
		);
		return client
			.get()
			.uri(uriBuilder -> uriBuilder
				.path(GET_DATE_OF_LAST_RECORD + this.stationType)
				.queryParams(
					createParams(
						"stationId", stationCode,
						"typeId", dataType,
						"period", period
					)
				)
				.build()
			)
			.retrieve()
			.bodyToMono(Date.class)
			.block();
    }

	@Override
	public void connectToDataCenterCollector() {
        // TODO authentification to writer
    }

    @Override
    public List<StationDto> fetchStations(String stationType, String origin) {
		LOG.info(
			"NonBlockingJSONPusher/fetchStations",
			v(
				"parameters", Utils.mapOf(
					"stationType", stationType,
					"origin", origin
				)
			),
			v("provenance", provenance)
		);
        StationDto[] object = client
			.get()
			.uri(uriBuilder->uriBuilder
				.path(stationType == null ? STATIONS + this.stationType : STATIONS + stationType)
				.queryParams(createParams("origin", origin))
				.build()
			)
			.retrieve()
            .bodyToMono(StationDto[].class)
			.block();
        return Arrays.asList(object);
    }

	private MultiValueMap<String, String> createParams(Object... params) {
		MultiValueMap<String, String> result = new LinkedMultiValueMap<>();
		for (int i = 0; i < params.length; i += 2) {
			if (params[i+1] != null) {
				result.add(params[i].toString(), params[i+1].toString());
			}
		}
		if (provenance != null) {
			result.add("prn", provenance.getDataCollector());
			result.add("prv", provenance.getDataCollectorVersion());
		}
		return result;
	}

}
