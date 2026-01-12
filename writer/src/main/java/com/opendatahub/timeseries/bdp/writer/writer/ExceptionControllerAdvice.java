// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer.writer;

import java.util.Map;

import org.hibernate.PropertyValueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.factories.SchemaFactoryWrapper;
import com.opendatahub.timeseries.bdp.dto.dto.ExceptionDto;
import com.opendatahub.timeseries.bdp.writer.dal.util.JPAException;
import com.opendatahub.timeseries.bdp.writer.dal.util.Log;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Catch and handle various exceptions. We use this to provide an unique representation of
 * all error messages to the API consumer.
 *
 * @author Peter Moser
 * @author Patrick Bertolla
 */
@ControllerAdvice
public class ExceptionControllerAdvice extends ResponseEntityExceptionHandler {
	private static final Logger LOG = LoggerFactory.getLogger(ExceptionControllerAdvice.class);

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleAll(HttpServletRequest request, Exception ex) {
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex, request);
	}
	@ExceptionHandler(JPAException.class)
	public ResponseEntity<Object> handleJPA(HttpServletRequest request, JPAException ex) {
		return buildResponse(HttpStatus.BAD_REQUEST, ex, request);
	}
	@ExceptionHandler(PersistenceException.class)
	public ResponseEntity<Object> handlePropertyValueException(HttpServletRequest request, PersistenceException ex) {
		if (ex.getCause() instanceof PropertyValueException) {
			JPAException jpaex = new JPAException("Invalid JSON: " + ex.getCause().getMessage());
			return buildResponse(HttpStatus.BAD_REQUEST, jpaex, request);
		}
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex, request);
	}

	private ResponseEntity<Object> buildResponse(HttpStatus httpStatus, Exception ex, HttpServletRequest request) {
		ExceptionDto exceptionDto;
		if (ex instanceof JPAException) {
			JPAException jpaex = (JPAException) ex;
			exceptionDto = jpaex.getExceptionDto();
			if (jpaex.getCause() != null) {
				exceptionDto.setDescription(jpaex.getCause().getMessage());
			}
			if (jpaex.getDtoClass() != null) {
				ObjectMapper mapper = new ObjectMapper();

				// TODO Generate visitor to filter out only properties in all sub-levels
		        SchemaFactoryWrapper visitor = new SchemaFactoryWrapper();
		        try {
			        mapper.acceptJsonFormatVisitor(jpaex.getDtoClass(), visitor);
			        JsonSchema schema = visitor.finalSchema();
			        String s = mapper.writer().writeValueAsString(schema);
			        JsonNode node = mapper.readTree(s).get("properties");

			        @SuppressWarnings("unchecked")
					Map<String, Object> map = mapper.convertValue(node, Map.class);
			        exceptionDto.setJsonSchema(map);
		        } catch (Exception e) {
		        	exceptionDto.setDescription(exceptionDto.getDescription() + " --- We had an error during schema information creation: " + e.getMessage());
		        }
			}
		} else {
			exceptionDto = new ExceptionDto();
		}
		if (exceptionDto.getDescription() == null)
			exceptionDto.setDescription(ex.getMessage());
		if (exceptionDto.getStatus() == null) {
			exceptionDto.setStatus(httpStatus.value());
			exceptionDto.setName(httpStatus.getReasonPhrase());
		} else if (exceptionDto.getName() == null) {
			exceptionDto.setName(HttpStatus.valueOf(exceptionDto.getStatus()).getReasonPhrase());
		}
		httpStatus = HttpStatus.valueOf(exceptionDto.getStatus());
		if (request != null) {
			request.setAttribute("exception_dto", exceptionDto);
			request.setAttribute("exception", ex);
		}
		LOG.error("API returned error due to exception", ex);
		return ResponseEntity.status(httpStatus).body(exceptionDto);
	}
}
