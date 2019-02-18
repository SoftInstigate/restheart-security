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
package io.uiam.plugins.init.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uiam.handlers.Request;
import io.uiam.handlers.security.AccessManagerHandler;
import io.uiam.plugins.PluginsRegistry;
import io.uiam.plugins.init.PluggableInitializer;
import io.uiam.plugins.interceptors.impl.EchoExampleRequestInterceptor;
import io.uiam.plugins.interceptors.impl.EchoExampleResponseInterceptor;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ExampleInitializer implements PluggableInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExampleInitializer.class);

    @Override
    public void init() {
        LOGGER.info("Testing initializer");
        LOGGER.info("\tallows GET requests to /foo/bar");
        LOGGER.info("\tadds a request and a response interceptor for /echo and /secho");

        // add a global security predicate
        AccessManagerHandler.getGlobalSecurityPredicates().add(new Predicate() {
            @Override
            public boolean resolve(HttpServerExchange exchange) {
                var request = Request.wrap(exchange);

                return request.isGet() 
                        && "/foo/bar".equals(exchange.getRequestPath());
            }
        });
        
        // add a test response transformer
        PluginsRegistry
                .getInstance()
                .getResponseInterceptors()
                .add(new EchoExampleResponseInterceptor());
        
        // add a test request transformer
        PluginsRegistry
                .getInstance()
                .getRequestInterceptors()
                .add(new EchoExampleRequestInterceptor());
    }
}
