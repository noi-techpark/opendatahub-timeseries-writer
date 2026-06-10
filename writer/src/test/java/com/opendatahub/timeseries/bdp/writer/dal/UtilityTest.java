// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.dal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import com.opendatahub.timeseries.bdp.writer.dal.util.PropertiesWithEnv;

public class UtilityTest {

	@Test
	public void testPropertiesWithEnv() {
		PropertiesWithEnv prop = new PropertiesWithEnv();
		prop.setProperty("a.test", "${__TEST_ENV_VAR}");
		try {
			prop.substitueEnv();
			fail("We expect an IllegalArgumentException!");
		} catch (IllegalArgumentException ex) {
			/* We expect this */
		}

		prop = new PropertiesWithEnv();
		prop.setProperty("a.test", "${__TEST_ENV_VAR:default-if-missing}");
		prop.substitueEnv();
		assertEquals("default-if-missing", prop.getProperty("a.test"));

		prop = new PropertiesWithEnv();
		prop.setProperty("a.test", "${__TEST_ENV_VAR:-default-if-missing}");
		prop.addEnv("__TEST_ENV_VAR", "this-is-a-test");
		prop.substitueEnv();
		assertEquals("this-is-a-test", prop.getProperty("a.test"));
	}

}
