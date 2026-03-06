// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import com.opendatahub.timeseries.bdp.client.util.NominatimException;
import com.opendatahub.timeseries.bdp.client.util.NominatimLocationLookupUtil;

public class NominatimLookupITTest {

	private NominatimLocationLookupUtil util = new NominatimLocationLookupUtil();

	// @Test
	// disabled because API not reachable. enable if it is again
	public void testLocationLookup() throws NominatimException {
		String lookupLocation = util.lookupLocation(11.45, 49.45);
		assertNotNull(lookupLocation);
		assertFalse(lookupLocation.isEmpty());
	}
	// @Test
	// disabled because API not reachable. enable if it is again
	public void testCoordinateLookup() throws NominatimException {
		Double[] coordinates = util.lookupCoordinates("via Fago 26 Bolzano");
		assertNotNull(coordinates);
	}
}