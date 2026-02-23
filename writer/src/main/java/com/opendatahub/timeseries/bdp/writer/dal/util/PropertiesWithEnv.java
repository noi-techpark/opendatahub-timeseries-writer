// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.timeseries.bdp.writer.dal.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertiesWithEnv extends Properties {

	private static final Logger LOG = LoggerFactory.getLogger(
		PropertiesWithEnv.class
	);

	private Map<String, String> localEnv = new HashMap<>();

	@Override
	public synchronized void load(InputStream inStream) throws IOException {
		super.load(inStream);
		substitueEnv();
	}

	public static PropertiesWithEnv fromActiveSpringProfile() throws IOException {
		String profile = System.getProperty("spring.profiles.active");
		if (profile == null || profile.trim().isEmpty()) {
			LOG.debug("Loading properties from the default profile");
			profile = "";
		} else {
			LOG.debug(
				"Loading properties from a custom profile named " + profile
			);
			profile = "-" + profile;
		}
		String filename = "application" + profile + ".properties";
		PropertiesWithEnv properties = new PropertiesWithEnv();
		InputStream resourceAsStream = PropertiesWithEnv.class.getClassLoader()
			.getResourceAsStream(filename);
		if (resourceAsStream == null) {
			throw new IOException("Loading properties failed: Unable to find " + filename);
		}
		properties.load(resourceAsStream);
		return properties;
	}

	public void substitueEnv() {
		Map<String, String> environment = new HashMap<>(localEnv);
		environment.putAll(System.getenv());
		StringSubstitutor sub = new StringSubstitutor(environment);
		sub.setEnableUndefinedVariableException(true);
		sub.setValueDelimiter(":");
		for (Entry<Object, Object> entry : super.entrySet()) {
			entry.setValue(sub.replace(entry.getValue()));
		}
	}

	public Map<String, String> getStringMap() {
		Map<String, String> result = new HashMap<>();
		for (final String name : super.stringPropertyNames()) {
			result.put(name, super.getProperty(name));
		}
		return result;
	}

	public void addEnv(final String key, final String value) {
		localEnv.put(key, value);
	}

	@Override
	public synchronized int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result =
			prime * result + ((localEnv == null) ? 0 : localEnv.hashCode());
		return result;
	}

	@Override
	public synchronized boolean equals(Object obj) {
		if (this == obj) return true;
		if (!super.equals(obj)) return false;
		if (getClass() != obj.getClass()) return false;
		PropertiesWithEnv other = (PropertiesWithEnv) obj;
		if (localEnv == null) {
			if (other.localEnv != null) return false;
		} else if (!localEnv.equals(other.localEnv)) return false;
		return true;
	}
}
