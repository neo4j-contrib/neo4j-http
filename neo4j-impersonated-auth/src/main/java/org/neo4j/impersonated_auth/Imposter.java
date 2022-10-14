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
public class Imposter {

	@Context
	public DependencyResolver dependencyResolver;

	@UserFunction("imposter.authenticate")
	public boolean authenticate(@Name("username") String username, @Name("password") String password) {

		var authManager = getAuthManager();
		try {
			var authToken = AuthToken.newBasicAuthToken(username, password);
			var ctx = authManager.login(authToken, ClientConnectionInfo.EMBEDDED_CONNECTION);
			return ctx.subject().getAuthenticationResult() == AuthenticationResult.SUCCESS;
		} catch (InvalidAuthTokenException e) {
			return false;
		}
	}

	private AuthManager getAuthManager() {
		return dependencyResolver.resolveDependency(AuthManager.class);
	}
}
