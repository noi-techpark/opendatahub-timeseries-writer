// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.client;

import jakarta.annotation.PostConstruct;

import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.opendatahub.timeseries.bdp.dto.dto.ProvenanceDto;

import static net.logstash.logback.argument.StructuredArguments.v;

/**
 * Basic configuration for any sender providing data to the writer
 *
 * @author Patrick Bertolla
 */
@Component
public abstract class DataPusher implements OpendatahubClient  {

	private static final Logger LOG = LoggerFactory.getLogger(DataPusher.class);

	public static final int STATION_CHUNK_SIZE = 25;

	protected Configuration config;
	protected String stationType;
	protected ProvenanceDto provenance;

	public abstract void connectToDataCenterCollector();
	public abstract String initStationType();
	public abstract ProvenanceDto defineProvenance();

	/**
	 * Instantiate a new data pusher with a typology defined in implementation
	 */
	@PostConstruct
	public void init() {
		connectToDataCenterCollector();
		this.stationType = initStationType();
		if (this.stationType == null)
			throw new IllegalStateException("You need to provide a valid data source type to continue");
		ProvenanceDto provenanceCandidate = defineProvenance();
		if (!ProvenanceDto.isValid(provenanceCandidate))
			throw new IllegalStateException("You need to provide a valid provenance to be able to send data");
		this.provenance = provenanceCandidate;
	}

	public String getStationType() {
		return stationType;
	}
	public void setStationType(String stationType) {
		this.stationType = stationType;
	}

	protected void logInfo(String msg, Object parameters) {
		LOG.info(
			msg,
			v("parameters", parameters),
			v("provenance", provenance)
		);
	}

	protected void logInfo(String msg) {
		logInfo(msg, null);
	}

	protected void logWarn(String msg, Object parameters) {
		LOG.warn(
			msg,
			v("parameters", parameters),
			v("provenance", provenance)
		);
	}

	protected void logWarn(String msg) {
		logWarn(msg, null);
	}


}
