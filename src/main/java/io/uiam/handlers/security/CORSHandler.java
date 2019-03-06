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

import static io.uiam.handlers.security.CORSHandler.CORSHeaders.ACCESS_CONTROL_ALLOW_CREDENTIAL;
import static io.uiam.handlers.security.CORSHandler.CORSHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.uiam.handlers.security.CORSHandler.CORSHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;
import static io.undertow.util.Headers.LOCATION_STRING;
import static io.undertow.util.Headers.ORIGIN;

import com.google.common.net.HttpHeaders;

import io.uiam.handlers.PipedHttpHandler;
import static io.uiam.plugins.authentication.PluggableTokenManager.AUTH_TOKEN_HEADER;
import static io.uiam.plugins.authentication.PluggableTokenManager.AUTH_TOKEN_LOCATION_HEADER;
import static io.uiam.plugins.authentication.PluggableTokenManager.AUTH_TOKEN_VALID_HEADER;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * The Access-Control-Expose-Headers header indicates which headers are safe to
 * expose to the API of a CORS API specification.
 *
 */
public class CORSHandler extends PipedHttpHandler {

    public static final String ALL_ORIGINS = "*";
    private final HttpHandler noPipedNext;

    /**
     * Creates a new instance of CORSHandler
     *
     * @param next
     */
    public CORSHandler(PipedHttpHandler next) {
        super(next);
        this.noPipedNext = null;
    }

    /**
     * Creates a new instance of CORSHandler
     *
     * @param next
     */
    public CORSHandler(HttpHandler next) {
        super(null);
        this.noPipedNext = next;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        injectAccessControlAllowHeaders(exchange);

        if (noPipedNext != null) {
            noPipedNext.handleRequest(exchange);
        } else {
            next(exchange);
        }
    }

    public static void injectAccessControlAllowHeaders(HttpServerExchange exchange) {
        HeaderMap requestHeaders = exchange.getRequestHeaders();
        HeaderMap responseHeaders = exchange.getResponseHeaders();

        if (!responseHeaders.contains(ACCESS_CONTROL_ALLOW_ORIGIN)) {
            if (requestHeaders.contains(ORIGIN)) {

                responseHeaders.add(ACCESS_CONTROL_ALLOW_ORIGIN,
                        requestHeaders.get(ORIGIN).getFirst());
            } else {
                responseHeaders.add(ACCESS_CONTROL_ALLOW_ORIGIN, ALL_ORIGINS);
            }
        }

        if (!responseHeaders.contains(ACCESS_CONTROL_ALLOW_CREDENTIAL)) {
            responseHeaders.add(ACCESS_CONTROL_ALLOW_CREDENTIAL, "true");
        }

        if (!responseHeaders.contains(ACCESS_CONTROL_EXPOSE_HEADERS)) {
            responseHeaders.add(ACCESS_CONTROL_EXPOSE_HEADERS,
                    LOCATION_STRING + ", " + Headers.ETAG + ", "
                    + AUTH_TOKEN_HEADER.toString() + ", "
                    + AUTH_TOKEN_VALID_HEADER.toString() + ", "
                    + AUTH_TOKEN_LOCATION_HEADER.toString() + ", "
                    + HttpHeaders.X_POWERED_BY);
        }
    }

    interface CORSHeaders {

        HttpString ACCESS_CONTROL_EXPOSE_HEADERS = HttpString.tryFromString("Access-Control-Expose-Headers");
        HttpString ACCESS_CONTROL_ALLOW_CREDENTIAL = HttpString.tryFromString("Access-Control-Allow-Credentials");
        HttpString ACCESS_CONTROL_ALLOW_ORIGIN = HttpString.tryFromString("Access-Control-Allow-Origin");
    }
}
