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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.Objects;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class PipedHttpHandler implements HttpHandler {

    protected static final String CONTENT_TYPE = "contentType";

    private  PipedHttpHandler next;

    /**
     * Creates a default instance of PipedHttpHandler with next = null
     */
    public PipedHttpHandler() {
        this(null);
    }

    /**
     *
     * @param next the next handler in this chain
     */
    public PipedHttpHandler(PipedHttpHandler next) {
        this.next = next;
    }
    
    public static PipedHttpHandler pipe(PipedHttpHandler... handlers) {
        if (Objects.isNull(handlers)) {
            return null;
        }
        
        for (var idx = 0; idx < handlers.length-1; idx++) {
            handlers[idx].setNext(handlers[idx+1]);
        }
        
        return handlers[0];
    }
    
    /**
     *
     * @param exchange
     * @throws Exception
     */
    public abstract void handleRequest(HttpServerExchange exchange) throws Exception;

    /**
     * @return the next PipedHttpHandler
     */
    protected PipedHttpHandler getNext() {
        return next;
    }
    
    /**
     * set the next PipedHttpHandler
     */
    protected void setNext(PipedHttpHandler next) {
        this.next = next;
    }

    protected void next(HttpServerExchange exchange) throws Exception {
        if (getNext() != null) {
            getNext().handleRequest(exchange);
        }
    }
}
