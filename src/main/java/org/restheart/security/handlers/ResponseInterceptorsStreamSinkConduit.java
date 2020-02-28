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

import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.restheart.handlers.exchange.AbstractExchange;
import org.restheart.handlers.exchange.ByteArrayResponse;
import org.restheart.security.plugins.PluginsRegistry;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

/**
 * conduit that executes response interceptors that don't require response
 * content; response interceptors that require response content are executed by
 * ModifiableContentSinkConduit.terminateWrites()
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ResponseInterceptorsStreamSinkConduit
        extends AbstractStreamSinkConduit<StreamSinkConduit> {

    static final Logger LOGGER = LoggerFactory.getLogger(ResponseInterceptorsStreamSinkConduit.class);

    private final StreamSinkConduit _next;

    /**
     * Construct a new instance.
     *
     * @param next
     * @param exchange
     */
    public ResponseInterceptorsStreamSinkConduit(StreamSinkConduit next,
            HttpServerExchange exchange) {
        super(next);
        this._next = next;

        if (!AbstractExchange.isInError(exchange)
                && !AbstractExchange.responseInterceptorsExecuted(exchange)) {
            AbstractExchange.setResponseInterceptorsExecuted(exchange);
            PluginsRegistry.getInstance()
                    .getResponseInterceptors()
                    .stream()
                    .filter(ri -> ri.isEnabled())
                    .filter(ri -> ri.getInstance().resolve(exchange))
                    // this conduit does not provide access to response content
                    .filter(ri -> !ri.getInstance().requiresResponseContent())
                    .forEachOrdered(ri -> {
                        LOGGER.debug("Executing response interceptor {} for {}",
                                ri.getInstance().getClass().getSimpleName(),
                                exchange.getRequestPath());

                        try {
                            ri.getInstance().handleRequest(exchange);
                        } catch (Exception ex) {
                            LOGGER.error("Error executing response interceptor {} for {}",
                                    ri.getInstance().getClass().getSimpleName(),
                                    exchange.getRequestPath(),
                                    ex);
                            AbstractExchange.setInError(exchange);
                            // set error message
                            ByteArrayResponse response = ByteArrayResponse
                                    .wrap(exchange);

                            response.endExchangeWithMessage(
                                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                    "Error executing response interceptor "
                                    + ri.getClass().getSimpleName(),
                                    ex);
                        }
                    });
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return _next.write(src);
    }

    @Override
    public long write(ByteBuffer[] dsts, int offs, int len) throws IOException {
        return _next.write(dsts, offs, len);
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return _next.transferFrom(src, position, count);
    }

    @Override
    public long transferFrom(final StreamSourceChannel src, final long count, final ByteBuffer throughBuffer) throws IOException {
        return _next.transferFrom(src, count, throughBuffer);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return _next.writeFinal(src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return _next.writeFinal(srcs, offset, length);
    }

    @Override
    public void terminateWrites() throws IOException {
        _next.terminateWrites();
    }
}
