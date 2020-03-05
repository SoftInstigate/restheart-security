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

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.ServerFixedLengthStreamSinkConduit;
import io.undertow.util.Headers;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.restheart.handlers.exchange.AbstractExchange;
import static org.restheart.handlers.exchange.AbstractExchange.MAX_BUFFERS;
import org.restheart.handlers.exchange.ByteArrayResponse;
import org.restheart.handlers.exchange.ProxableResponse;
import org.restheart.handlers.exchange.Response;
import org.restheart.plugins.security.InterceptPoint;
import org.restheart.security.plugins.PluginsRegistry;
import org.restheart.utils.BuffersUtils;
import org.restheart.utils.HttpStatus;
import static org.restheart.utils.PluginUtils.interceptPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

/**
 * a conduit that buffers data allowing to modify it it also responsible of
 * executing response interceptors when terminateWrites() is called
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ModifiableContentSinkConduit
        extends AbstractStreamSinkConduit<StreamSinkConduit> {

    static final Logger LOGGER = LoggerFactory.getLogger(ModifiableContentSinkConduit.class);

    //private ByteBuffer data = null;
    private final HttpServerExchange exchange;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     * @param exchange
     */
    public ModifiableContentSinkConduit(StreamSinkConduit next,
            HttpServerExchange exchange) {
        super(next);
        this.exchange = exchange;

        resetBufferPool(exchange);
    }

    /**
     * init buffers pool with a single, empty buffer.
     *
     * @param exchange
     * @return
     */
    private void resetBufferPool(HttpServerExchange exchange) {
        var buffers = new PooledByteBuffer[MAX_BUFFERS];
        exchange.putAttachment(ProxableResponse.BUFFERED_RESPONSE_DATA,
                buffers);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return BuffersUtils.append(src,
                exchange.getAttachment(ProxableResponse.BUFFERED_RESPONSE_DATA),
                exchange);
    }

    @Override
    public long write(ByteBuffer[] dsts, int offs, int len) throws IOException {
        for (int i = offs; i < len; ++i) {
            if (dsts[i].hasRemaining()) {
                return write(dsts[i]);
            }
        }
        return 0;
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public void terminateWrites() throws IOException {
        var resp = ByteArrayResponse.wrap(exchange);

        if (!AbstractExchange.isInError(exchange)
                && !AbstractExchange.responseInterceptorsExecuted(exchange)) {
            AbstractExchange.setResponseInterceptorsExecuted(exchange);
            
            executeResponseAsyncInterceptor(exchange);
            executeResponseInterceptor(exchange);
        }

        PooledByteBuffer[] dests = resp.getRawContent();

        updateContentLenght(exchange, dests);

        for (PooledByteBuffer dest : dests) {
            if (dest != null) {
                next.write(dest.getBuffer());
            }
        }

        next.terminateWrites();
    }

    private void executeResponseInterceptor(HttpServerExchange exchange) {
        var resp = ByteArrayResponse.wrap(exchange);

        PluginsRegistry.getInstance()
                .getInterceptors()
                .stream()
                .filter(ri -> ri.isEnabled())
                .map(ri -> ri.getInstance())
                .filter(ri -> ri.resolve(exchange))
                .filter(ri -> interceptPoint(ri) == InterceptPoint.RESPONSE)
                .forEachOrdered(ri -> {
                    LOGGER.debug("Executing response interceptor {} for {}",
                            ri.getClass().getSimpleName(),
                            exchange.getRequestPath());

                    try {
                        ri.handle(exchange);
                    }
                    catch (Exception ex) {
                        LOGGER.error("Error executing response interceptor {} for {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                ex);
                        AbstractExchange.setInError(exchange);
                        // set error message
                        ByteArrayResponse response = ByteArrayResponse
                                .wrap(exchange);

                        // dump bufferd content
                        BuffersUtils.dump("content buffer "
                                + exchange.getRequestPath(),
                                resp.getRawContent());

                        response.endExchangeWithMessage(
                                HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                "Error executing response interceptor "
                                + ri.getClass().getSimpleName(),
                                ex);
                    }
                });
    }

    private void executeResponseAsyncInterceptor(HttpServerExchange exchange) {
        var resp = ByteArrayResponse.wrap(exchange);

        PluginsRegistry.getInstance()
                .getInterceptors()
                .stream()
                .filter(ri -> ri.isEnabled())
                .map(ri -> ri.getInstance())
                .filter(ri -> ri.resolve(exchange))
                .filter(ri -> interceptPoint(ri) == InterceptPoint.RESPONSE_ASYNC)
                .forEachOrdered(ri -> {
                    exchange.getConnection().getWorker().execute(() -> {

                        LOGGER.debug("Executing response interceptor {} for {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath());

                        try {
                            ri.handle(exchange);
                        }
                        catch (Exception ex) {
                            LOGGER.error("Error executing response interceptor {} for {}",
                                    ri.getClass().getSimpleName(),
                                    exchange.getRequestPath(),
                                    ex);
                            AbstractExchange.setInError(exchange);
                            // set error message
                            ByteArrayResponse response = ByteArrayResponse
                                    .wrap(exchange);

                            // dump bufferd content
                            BuffersUtils.dump("content buffer "
                                    + exchange.getRequestPath(),
                                    resp.getRawContent());

                            response.endExchangeWithMessage(
                                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                    "Error executing response interceptor "
                                    + ri.getClass().getSimpleName(),
                                    ex);
                        }
                    });
                });
    }

    private void updateContentLenght(HttpServerExchange exchange, PooledByteBuffer[] dests) {
        long length = 0;

        for (PooledByteBuffer dest : dests) {
            if (dest != null) {
                length += dest.getBuffer().limit();
            }
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, length);

        // need also to update lenght of ServerFixedLengthStreamSinkConduit
        if (next instanceof ServerFixedLengthStreamSinkConduit) {
            Method m;

            try {
                m = ServerFixedLengthStreamSinkConduit.class.getDeclaredMethod(
                        "reset",
                        long.class,
                        HttpServerExchange.class);
                m.setAccessible(true);
            }
            catch (NoSuchMethodException | SecurityException ex) {
                LOGGER.error("could not find ServerFixedLengthStreamSinkConduit.reset method", ex);
                throw new RuntimeException("could not find ServerFixedLengthStreamSinkConduit.reset method", ex);
            }

            try {
                m.invoke(next, length, exchange);
            }
            catch (Throwable ex) {
                LOGGER.error("could not access BUFFERED_REQUEST_DATA field", ex);
                throw new RuntimeException("could not access BUFFERED_REQUEST_DATA field", ex);
            }
        } else {
            LOGGER.warn("updateContentLenght() next is {}", next.getClass().getSimpleName());
        }
    }
}
