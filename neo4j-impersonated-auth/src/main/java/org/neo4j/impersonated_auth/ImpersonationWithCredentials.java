/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.impersonated_auth;

import org.neo4j.common.DependencyResolver;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.internal.kernel.api.connectioninfo.ClientConnectionInfo;
import org.neo4j.internal.kernel.api.security.AuthenticationResult;
import org.neo4j.kernel.api.security.AuthManager;
import org.neo4j.kernel.api.security.AuthToken;
import org.neo4j.kernel.api.security.exception.InvalidAuthTokenException;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

/**
 * UDF that is the target of a Spring authentication provider.
 *
 * @author Michael J. Simons
 */
public class ImpersonationWithCredentials {

	/**
	 * Needs to be public because Neo4j and required to access an instance of {@link AuthManager}.
	 */
	@Context
	public DependencyResolver dependencyResolver;

	/**
	 * See {@link AuthenticationResult#SUCCESS}.
	 */
	public static final String SUCCESS = "SUCCESS";

	/**
	 * See {@link AuthenticationResult#FAILURE}.
	 */
	public static final String FAILURE = "FAILURE";

	/**
	 * Authenticates based on username and password. The returned value will either be {@link #SUCCESS} or {@link #FAILURE},
	 * modelled after the enum {@link AuthenticationResult}, but I don't want to depend on that directly.
	 *
	 * @param username The username to authenticate
	 * @param password The password to use
	 * @return Status about authentication
	 */
	@UserFunction("impersonation.authenticate")
	public String authenticate(@Name("username") String username, @Name("password") byte[] password) {

		var authManager = getAuthManager();
		try {
			var authToken = AuthToken.newBasicAuthToken(username, password);
			var ctx = authManager.login(authToken, ClientConnectionInfo.EMBEDDED_CONNECTION);

			return ctx.subject().getAuthenticationResult() == AuthenticationResult.SUCCESS ? SUCCESS : FAILURE;
		} catch (InvalidAuthTokenException e) {
			return FAILURE;
		}
	}

	private AuthManager getAuthManager() {
		return dependencyResolver.resolveDependency(AuthManager.class);
	}
}
