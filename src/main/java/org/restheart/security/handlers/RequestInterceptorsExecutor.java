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
package org.restheart.security.handlers;

import org.restheart.security.handlers.exchange.AbstractExchange;
import org.restheart.security.plugins.PluginsRegistry;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.restheart.security.utils.LambdaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.restheart.security.plugins.RequestInterceptor;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestInterceptorsExecutor extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(RequestInterceptorsExecutor.class);

    /**
     *
     */
    public RequestInterceptorsExecutor() {
        super(null);
    }

    /**
     * @param next
     */
    public RequestInterceptorsExecutor(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<RequestInterceptor> interceptors = PluginsRegistry
                .getInstance()
                .getRequestInterceptors();

        interceptors.stream()
                .filter(ri -> ri.resolve(exchange))
                .forEachOrdered(ri -> {
                    try {
                        LOGGER.debug("Executing request interceptor {} for {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath());

                        ri.handleRequest(exchange);
                    } catch (Exception ex) {
                        LOGGER.error("Error executing request interceptor {} for {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                ex);
                        AbstractExchange.setInError(exchange);
                        LambdaUtils.throwsSneakyExcpetion(ex);
                    }
                });

        next(exchange);
    }
}
