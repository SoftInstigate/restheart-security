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
package io.uiam.handlers.applicationlogic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.util.Map;
import java.util.Set;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.RequestContext;
import static io.uiam.security.handlers.IAuthToken.AUTH_TOKEN_HEADER;
import static io.uiam.security.handlers.IAuthToken.AUTH_TOKEN_LOCATION_HEADER;
import static io.uiam.security.handlers.IAuthToken.AUTH_TOKEN_VALID_HEADER;
import io.uiam.utils.HttpStatus;
import io.uiam.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetRoleHandler extends ApplicationLogicHandler {

    /**
     * the key for the url property.
     */
    public static final String URL_KEY = "url";

    private String url;

    /**
     * Creates a new instance of GetRoleHandler
     *
     * @param next
     * @param args
     * @throws Exception
     */
    public GetRoleHandler(PipedHttpHandler next, Map<String, Object> args) throws Exception {
        super(next, args);

        if (args == null) {
            throw new IllegalArgumentException("args cannot be null");
        }

        this.url = (String) args.get(URL_KEY);
    }

    /**
     * Handles the request.
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.isOptions()) {
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET");
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge, " + AUTH_TOKEN_HEADER + ", " + AUTH_TOKEN_VALID_HEADER + ", " + AUTH_TOKEN_LOCATION_HEADER);
            exchange.setStatusCode(HttpStatus.SC_OK);
            exchange.endExchange();
        } else if (context.isGet()) {
            if ((exchange.getSecurityContext() == null
                    || exchange.getSecurityContext().getAuthenticatedAccount() == null
                    || exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal() == null)
                    || !(context.getMappedRequestUri().equals(URLUtils.removeTrailingSlashes(url) + "/" + exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName()))) {

                {
                    exchange.setStatusCode(HttpStatus.SC_FORBIDDEN);

                    // REMOVE THE AUTH TOKEN HEADERS!!!!!!!!!!!
                    exchange.getResponseHeaders().remove(AUTH_TOKEN_HEADER);
                    exchange.getResponseHeaders().remove(AUTH_TOKEN_VALID_HEADER);
                    exchange.getResponseHeaders().remove(AUTH_TOKEN_LOCATION_HEADER);

                    exchange.endExchange();
                    return;
                }

            } else {
                JsonObject root = new JsonObject();
                
                Set<String> _roles = exchange.getSecurityContext().getAuthenticatedAccount().getRoles();

                JsonArray roles = new JsonArray();

                _roles.forEach((role) -> {
                    roles.add(new JsonPrimitive(role));
                });

                root.add("authenticated", new JsonPrimitive(true));
                root.add("roles", roles);
                
                exchange.getResponseSender().send(root.toString());
            }

            //TODO, set application/json as static final var
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.endExchange();
        } else {
            exchange.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }
}
