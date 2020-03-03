/*
 * RESTHeart Security
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
package org.restheart.security.plugins.services;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.util.Map;
import org.restheart.ConfigurationException;
import org.restheart.plugins.ConfigurablePlugin;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Service;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_VALID_HEADER;
import org.restheart.security.plugins.PluginsRegistry;
import org.restheart.security.plugins.authenticators.BaseAccount;
import org.restheart.utils.HttpStatus;

/**
 * allows to get and invalidate the user auth token generated by RndTokenManager
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "rndTokenService",
        description = "allows to get and invalidate the user auth token generated by RndTokenManager",
        enabledByDefault = true,
        defaultURI = "/tokens"
)
public class RndTokenService implements Service {

    // used to compare the requested URI containing escaped chars
    private static final Escaper ESCAPER = UrlEscapers.urlPathSegmentEscaper();

    private Map<String, Object> confArgs = null;
    
    /**
     * init the service
     * @param confArgs
     * @throws org.restheart.ConfigurationException
     */
    @OnInit
    public void init(Map<String, Object> confArgs)
            throws ConfigurationException {
        this.confArgs = confArgs;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handle(HttpServerExchange exchange) throws Exception {
        if (exchange.getRequestPath().startsWith(getUri())
                && exchange.getRequestPath().length() >= (getUri().length() + 2)
                && Methods.OPTIONS.equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().put(
                    HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, DELETE")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"),
                            "Accept, Accept-Encoding, Authorization, Content-Length, "
                            + "Content-Type, Host, Origin, X-Requested-With, "
                            + "User-Agent, No-Auth-Challenge");

            exchange.setStatusCode(HttpStatus.SC_OK);
            exchange.endExchange();
            return;
        }

        if (exchange.getSecurityContext() == null
                || exchange.getSecurityContext().getAuthenticatedAccount() == null
                || exchange.getSecurityContext().getAuthenticatedAccount()
                        .getPrincipal() == null) {
            exchange.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            exchange.endExchange();
            return;
        }

        if (!((getUri() + "/" + exchange.getSecurityContext()
                .getAuthenticatedAccount().getPrincipal().getName())
                .equals(exchange.getRequestURI()))
                && !(ESCAPER.escape(getUri() + "/" + exchange.getSecurityContext()
                        .getAuthenticatedAccount().getPrincipal().getName()))
                        .equals(exchange.getRequestURI())) {
            exchange.setStatusCode(HttpStatus.SC_FORBIDDEN);
            exchange.endExchange();
            return;
        }

        if (Methods.GET.equals(exchange.getRequestMethod())) {
            JsonObject resp = new JsonObject();

            resp.add("auth_token", new JsonPrimitive(exchange.getResponseHeaders()
                    .get(AUTH_TOKEN_HEADER).getFirst()));

            resp.add("auth_token_valid_until",
                    new JsonPrimitive(exchange.getResponseHeaders()
                            .get(AUTH_TOKEN_VALID_HEADER).getFirst()));

            exchange.setStatusCode(HttpStatus.SC_OK);
            // TODO use static var for content type
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send(resp.toString());
            exchange.endExchange();
        } else if (Methods.DELETE.equals(exchange.getRequestMethod())) {
            BaseAccount account = new BaseAccount(exchange.getSecurityContext()
                    .getAuthenticatedAccount().getPrincipal().getName(),
                    null);

            invalidate(account);

            removeAuthTokens(exchange);
            exchange.setStatusCode(HttpStatus.SC_NO_CONTENT);
            exchange.endExchange();
        } else {
            exchange.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }

    private void invalidate(Account account) {
        var tokenManager = PluginsRegistry
                .getInstance()
                .getTokenManager();

        if (tokenManager == null) {
            throw new IllegalStateException("Error, cannot invalidate, "
                    + "token manager not active");
        }

        tokenManager.getInstance().invalidate(account);
    }

    private void removeAuthTokens(HttpServerExchange exchange) {
        exchange.getResponseHeaders().remove(AUTH_TOKEN_HEADER);
        exchange.getResponseHeaders().remove(AUTH_TOKEN_VALID_HEADER);
        exchange.getResponseHeaders().remove(AUTH_TOKEN_LOCATION_HEADER);
    }

    private String getUri() {
        if (confArgs == null) {
            return "/tokens";
        }
        
        try {
            return ConfigurablePlugin.argValue(confArgs, "uri");
        }
        catch (ConfigurationException ex) {
            return "/tokens";
        }
    }
}
