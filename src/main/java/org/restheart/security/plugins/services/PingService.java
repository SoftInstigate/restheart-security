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

import static org.restheart.security.plugins.ConfigurablePlugin.argValue;

import java.util.Map;

import org.restheart.security.handlers.exchange.JsonRequest;
import org.restheart.security.handlers.PipedHttpHandler;
import org.restheart.security.plugins.Service;
import org.restheart.security.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PingService extends Service {

    private final String msg;

    /**
     *
     * @param next
     * @param args
     */
    public PingService(PipedHttpHandler next,
            String name,
            String uri,
            Boolean secured,
            Map<String, Object> args)
            throws Exception {
        super(next, name, uri, secured, args);
        this.msg = argValue(args, "msg");
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = JsonRequest.wrap(exchange);

        if (request.isGet()) {
            exchange.setStatusCode(HttpStatus.SC_OK);
            exchange.getResponseSender().send(msg);
            exchange.endExchange();
        } else {
            exchange.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
            exchange.endExchange();
        }
    }
}
