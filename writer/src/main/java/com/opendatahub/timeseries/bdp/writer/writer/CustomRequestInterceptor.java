// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.timeseries.bdp.writer.writer;

import static net.logstash.logback.argument.StructuredArguments.v;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.opendatahub.timeseries.bdp.dto.dto.ExceptionDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Interceptor on each http request to log its name and performance stats
 */
@Component
public class CustomRequestInterceptor implements HandlerInterceptor {

	private static final Logger LOG = LoggerFactory.getLogger(
		CustomRequestInterceptor.class
	);

	@Override
	public boolean preHandle(
		HttpServletRequest request,
		HttpServletResponse response,
		Object handler
	) {
		Instant now = Instant.now();
		String uuid = UUID.randomUUID().toString();
		@SuppressWarnings("unchecked")
		Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		String path = request.getRequestURI().substring(request.getContextPath().length());
		Map<String, Object> logPayload = new HashMap<>();
		logPayload.put("request_state", "START");
		logPayload.put("provenance_name", request.getParameter("prn"));
		logPayload.put("provenance_version", request.getParameter("prv"));
		logPayload.put("request_uuid", uuid);
		logPayload.put("request_path", path);
		logPayload.put("request_path_variables", pathVariables);
		logPayload.put("request_path_base", getRequestBasePath(path, pathVariables));
		logPayload.put("start_time", now.toString());
		logPayload.put("start_epochmilli", now.toEpochMilli());
		request.setAttribute("log_payload", logPayload);
		return true;
	}

	@Override
	public void afterCompletion(
		HttpServletRequest request,
		HttpServletResponse response,
		Object handler,
		Exception ex
	) {
		Instant now = Instant.now();
		@SuppressWarnings("unchecked")
		Map<String, Object> logPayload = (Map<String, Object>) request.getAttribute("log_payload");
		long startTime = (Long) logPayload.get("start_epochmilli");
		logPayload.put("end_time", now.toString());
		logPayload.put("end_epochmilli", now.toEpochMilli());
		logPayload.put("response_time_ms", now.toEpochMilli() - startTime);
		logPayload.put("http_status_code", response.getStatus());
		logPayload.put("http_request_parameters", request.getParameterMap());
		ExceptionDto exceptionDto = (ExceptionDto) request.getAttribute("exception_dto");
		Exception exception = (Exception) request.getAttribute("exception");
		if (exception == null && exceptionDto == null && response.getStatus() < 400) {
			logPayload.put("request_state", "SUCCESS");
			if (LOG.isDebugEnabled()) {
				System.err.println(logPayload);
			}
			LOG.info("API call", v("api_request_info", logPayload));
		} else {
			logPayload.put("exception_dto", exceptionDto);
			if (response.getStatus() < 500) {
				logPayload.put("request_state", "WARNING");
				request.setAttribute("level", "WARN");
				LOG.warn("API call", v("api_request_info", logPayload));
			} else {
				logPayload.put("exception", exception != null ? Arrays.toString(exception.getStackTrace()) : "<null>");
				logPayload.put("request_state", "ERROR");
				LOG.error("API call", v("api_request_info", logPayload));
				if (LOG.isDebugEnabled() && exception != null) {
					exception.printStackTrace(System.err);
				}
			}
		}
	}

	private String getRequestBasePath(String path, Map<String, String> pathVariables) {
		path = path.trim();
		int pathLength = path.length();
		for (String val : pathVariables.values())
			pathLength -= val.length() + 1;
		if (path.endsWith("/"))
			pathLength--;
		return path.substring(0, pathLength);
	}
}
