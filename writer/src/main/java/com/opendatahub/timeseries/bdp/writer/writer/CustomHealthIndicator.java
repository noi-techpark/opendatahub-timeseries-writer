// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.timeseries.bdp.writer.writer;

import static net.logstash.logback.argument.StructuredArguments.v;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.opendatahub.timeseries.bdp.dto.utils.Utils;

@Component
public class CustomHealthIndicator implements HealthIndicator {

	private static final Logger LOG = LoggerFactory.getLogger(CustomHealthIndicator.class);

    @Override
    public Health health() {
		LOG.info("Health check", v("api_health_info", Utils.mapOf("status", "HEALTHY")));
        return Health.up().build();
    }
}
