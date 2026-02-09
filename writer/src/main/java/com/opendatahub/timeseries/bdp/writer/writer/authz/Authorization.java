// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.opendatahub.timeseries.bdp.writer.writer.authz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.keycloak.authorization.client.AuthorizationDeniedException;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.resource.AuthorizationResource;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.Permission;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;

public class Authorization {
	public static final String ATTRIBUTE_AUTHORIZATION = "bdp_authz";
	private final static Logger log = LoggerFactory.getLogger(Authorization.class);

	private AuthzClient authz;
	private AuthorizationResource authzRes;
	private String clientId;

	public Authorization(AuthzClient client, String accessToken) {
		this.authz = client;
		this.authzRes = client.authorization(accessToken); // Authorization resource initialized with the requestor's credentials
		this.clientId = authz.getConfiguration().getResource();
	}

	public boolean hasAnyAuthorization() {
		try {
			authzRes.authorize();
			return true;
		} catch (AuthorizationDeniedException e) {
			log.info("UMA: User does not have any relevant authorizations");
			return false;
		} catch (Exception e) {
			log.error("UMA: Unexpected error getting user authorizations: {}", e.getMessage());
			log.debug("Dumping stack trace", e);
			return false;
		}
	}

	public List<ResourceRepresentation> getAuthorizedResources(String type, String scope) {
		// https://github.com/keycloak/keycloak/issues/27483
		var req = new AuthorizationRequest();
		req.setMetadata(new AuthorizationRequest.Metadata());
		req.setAudience(clientId);
		req.setScope(scope);

		var perms = authzRes.getPermissions(req);

		Set<String> resourceIds;
		// https://github.com/keycloak/keycloak/issues/16520
		// runtime type is wrong until fix is merged
		if (!perms.isEmpty() && ((List) perms).get(0) instanceof Map) {
			// Forcefully cast this to map (which it actually is at runtime)
			List<Object> tmp = new ArrayList<>(perms);
			resourceIds = tmp.stream()
					.map(o -> (Map<String, String>) o)
					.map(m -> m.get("rsid"))
					.collect(Collectors.toSet());
		} else {
			resourceIds = perms.stream().map(Permission::getResourceId).collect(Collectors.toSet());
		}

		return getAllResources().stream()
			.filter(r -> resourceIds.contains(r.getId()))
			.toList();
	}

	@Cacheable("resources")
	private List<ResourceRepresentation> getAllResources() {
		// get all resource details from Keycloak
		// TODO: caching
		var protect = authz.protection();
		return protect.resource().<List<ResourceRepresentation>>find(null, null, null, null, null, null, false, true,
				null, null);
	}
}
