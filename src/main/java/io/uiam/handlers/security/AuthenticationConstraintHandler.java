/*
 * uIAM - the IAM for microservices
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uiam.handlers.security;

import io.uiam.handlers.PipedHttpHandler;
import io.uiam.plugins.authorization.PluggableAccessManager;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthenticationConstraintHandler extends PipedHttpHandler {

    PluggableAccessManager am;

    /**
     *
     * @param next
     * @param am
     */
    public AuthenticationConstraintHandler(PipedHttpHandler next, PluggableAccessManager am) {
        super(next);
        this.am = am;
    }
    
    protected boolean isAuthenticationRequired(final HttpServerExchange exchange) {
        return am.isAuthenticationRequired(exchange);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (isAuthenticationRequired(exchange)) {
            SecurityContext scontext = exchange.getSecurityContext();
            scontext.setAuthenticationRequired();
        }

        next(exchange);
    }
}