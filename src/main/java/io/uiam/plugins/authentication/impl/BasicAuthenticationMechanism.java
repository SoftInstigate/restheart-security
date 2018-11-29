/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.plugins.authentication.impl;

import io.uiam.plugins.IDMCacheSingleton;
import io.uiam.plugins.PluginConfigurationException;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BasicAuthenticationMechanism
        extends io.undertow.security.impl.BasicAuthenticationMechanism
        implements PluggableAuthenticationMechanism {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicAuthenticationMechanism.class);

    public static final String SILENT_HEADER_KEY = "No-Auth-Challenge";
    public static final String SILENT_QUERY_PARAM_KEY = "noauthchallenge";

    public BasicAuthenticationMechanism(String mechanismName, Map<String, Object> args) throws PluginConfigurationException {
        super(realmName(args),
                mechanismName,
                false,
                IDMCacheSingleton
                        .getInstance()
                        .getIdentityManager(identityManagerName(args)));

    }

    private static String realmName(Map<String, Object> args) throws PluginConfigurationException {
        if (args == null || !args.containsKey("realm") || !(args.get("realm") instanceof String)) {
            throw new PluginConfigurationException("BasicAuthenticationMechanism requires string argument 'realm'");
        } else {
            return (String) args.get("realm");
        }
    }

    private static String identityManagerName(Map<String, Object> args) throws PluginConfigurationException {
        if (args == null || !args.containsKey("idm") || !(args.get("idm") instanceof String)) {
            throw new PluginConfigurationException("BasicAuthenticationMechanism requires string argument 'idm'");
        } else {
            return (String) args.get("idm");
        }
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        if (exchange.getRequestHeaders().contains(SILENT_HEADER_KEY) || exchange.getQueryParameters().containsKey(SILENT_QUERY_PARAM_KEY)) {
            return new ChallengeResult(true, UNAUTHORIZED);
        } else {
            return super.sendChallenge(exchange, securityContext);
        }
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
        return super.authenticate(exchange, securityContext);
    }
}
