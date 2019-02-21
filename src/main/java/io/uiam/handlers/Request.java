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
package io.uiam.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import static io.uiam.handlers.RequestContentInjector.MAX_CONTENT_SIZE;
import io.undertow.connector.PooledByteBuffer;

import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Buffers;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Request {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(Request.class);

    public static final String FORM_URLENCODED = "application/x-www-form-urlencoded";
    public static final String MULTIPART = "multipart/form-data";

    // other constants
    public static final String SLASH = "/";
    public static final String PATCH = "PATCH";
    public static final String UNDERSCORE = "_";

    private final HttpServerExchange wrapped;

    private static final JsonParser PARSER = new JsonParser();

    private static final AttachmentKey<JsonElement> CONTENT_AS_JSON
            = AttachmentKey.create(JsonElement.class);

    private static final AttachmentKey<Long> START_TIME_KEY
            = AttachmentKey.create(Long.class);

    private static final AttachmentKey<Map<String, List<String>>> XFORWARDED_HEADERS
            = AttachmentKey.create(Map.class);

    public Request(HttpServerExchange exchange) {
        this.wrapped = exchange;
    }

    public static Request wrap(HttpServerExchange exchange) {
        return new Request(exchange);
    }

    private static METHOD selectMethod(HttpString _method) {
        if (Methods.GET.equals(_method)) {
            return METHOD.GET;
        } else if (Methods.POST.equals(_method)) {
            return METHOD.POST;
        } else if (Methods.PUT.equals(_method)) {
            return METHOD.PUT;
        } else if (Methods.DELETE.equals(_method)) {
            return METHOD.DELETE;
        } else if (PATCH.equals(_method.toString())) {
            return METHOD.PATCH;
        } else if (Methods.OPTIONS.equals(_method)) {
            return METHOD.OPTIONS;
        } else {
            return METHOD.OTHER;
        }
    }

    /**
     *
     * @return the request method
     */
    public METHOD getMethod() {
        return selectMethod(getWrapped().getRequestMethod());
    }

    /**
     * @return the request content as byte[]
     */
    public byte[] getContent() throws IOException {
        ByteBuffer content;
        try {
            content = readByteBuffer(getBufferedContent());
        }
        catch (Exception ex) {
            throw new IOException("Error getting request content", ex);
        }

        byte[] ret = new byte[content.limit()];

        content.get(ret);

        return ret;
    }

    /**
     * @return the request content as String
     */
    public String getContentAsText() throws IOException {
        String content;

        if (getWrapped().getAttachment(CONTENT_AS_JSON) != null) {
            content = getWrapped().getAttachment(CONTENT_AS_JSON).toString();
        } else {
            try {
                content = new String(getContent(), Charset.defaultCharset());
            }
            catch (Exception ex) {
                throw new IOException("Error getting request content", ex);
            }
        }

        return content;
    }

    /**
     * @return the request body as Json
     * @throws java.io.IOException
     * @throws com.google.gson.JsonSyntaxException
     */
    public JsonElement getContentAsJson()
            throws IOException, JsonSyntaxException {
        if (getWrapped().getAttachment(CONTENT_AS_JSON) == null) {
            getWrapped().putAttachment(CONTENT_AS_JSON,
                    PARSER.parse(getContentAsText()));
        }

        return getWrapped().getAttachment(CONTENT_AS_JSON);
    }

    private PooledByteBuffer[] getBufferedContent() {
        if (!isContentAvailable()) {
            throw new IllegalStateException("Request content is not available. "
                    + "Add a Request Inteceptor overriding requiresContent() "
                    + "to return true in order to make the content available.");
        }

        return getWrapped().getAttachment(getBufferedRequestDataKey());
    }

    private AttachmentKey<PooledByteBuffer[]> getBufferedRequestDataKey() {
        if (!isContentAvailable()) {
            throw new IllegalStateException("Request content is not available. "
                    + "Add a Request Inteceptor overriding requiresContent() "
                    + "to return true in order to make the content available.");
        }

        Field f;

        try {
            f = HttpServerExchange.class.getDeclaredField("BUFFERED_REQUEST_DATA");
            f.setAccessible(true);
        }
        catch (NoSuchFieldException | SecurityException ex) {
            throw new RuntimeException("could not find BUFFERED_REQUEST_DATA field", ex);
        }

        try {
            return (AttachmentKey<PooledByteBuffer[]>) f.get(getWrapped());
        }
        catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new RuntimeException("could not access BUFFERED_REQUEST_DATA field", ex);
        }
    }

    /**
     * If content is modified in a proxied request, synch modification to
     * BUFFERED_REQUEST_DATA. This is not required for PluggableService.
     *
     * @param exchange
     * @throws IOException
     */
    public void syncBufferedContent(HttpServerExchange exchange) throws IOException {
        if (!isContentAvailable()) {
            return;
        }
        
        // not getContent() as json might be availabe as json and modified
        var src = ByteBuffer.wrap(getContentAsText().getBytes());

        PooledByteBuffer[] dests = getWrapped()
                .getAttachment(getBufferedRequestDataKey());

        int pidx = 0;

        long copied = 0;

        while (src.hasRemaining() && pidx < dests.length) {
            if (dests[pidx] == null) {
                dests[pidx] = exchange.getConnection().getByteBufferPool().allocate();
            }

            ByteBuffer dest = dests[pidx].getBuffer();

            copied += Buffers.copy(dest, src);

            // very important, I lost a day for this!
            dest.flip();
            pidx++;
        }

        exchange.getRequestHeaders().put(Headers.CONTENT_LENGTH, copied);
    }

    /**
     * @return the requestStartTime
     */
    public Long getStartTime() {
        return getWrapped().getAttachment(START_TIME_KEY);
    }

    /**
     * @param requestStartTime the requestStartTime to set
     */
    public void setStartTime(Long requestStartTime) {
        getWrapped().putAttachment(START_TIME_KEY, requestStartTime);
    }

    /**
     * @return the responseContentType
     */
    public String getContentType() {
        return getWrapped().getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
    }

    /**
     * @return the authenticatedAccount
     */
    public Account getAuthenticatedAccount() {
        return getWrapped().getSecurityContext().getAuthenticatedAccount();
    }

    /**
     * Add the header X-Forwarded-[key] to the proxied request; use it to pass
     * to the bbackend information otherwise lost proxying the request.
     *
     * @param key
     * @param value
     */
    public void addXForwardedHeader(String key, String value) {
        if (wrapped.getAttachment(XFORWARDED_HEADERS) == null) {
            wrapped.putAttachment(XFORWARDED_HEADERS, new LinkedHashMap<>());

        }

        var values = wrapped.getAttachment(XFORWARDED_HEADERS).get(key);

        if (values == null) {
            values = new ArrayList<>();
            wrapped.getAttachment(XFORWARDED_HEADERS).put(key, values);
        }

        values.add(value);
    }

    public Map<String, List<String>> getXForwardedHeaders() {
        return getWrapped().getAttachment(XFORWARDED_HEADERS);
    }

    public enum METHOD {
        GET, POST, PUT, DELETE, PATCH, OPTIONS, OTHER
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.DELETE
     */
    public boolean isDelete() {
        return getMethod() == METHOD.DELETE;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.GET
     */
    public boolean isGet() {
        return getMethod() == METHOD.GET;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.OPTIONS
     */
    public boolean isOptions() {
        return getMethod() == METHOD.OPTIONS;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PATCH
     */
    public boolean isPatch() {
        return getMethod() == METHOD.PATCH;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.POST
     */
    public boolean isPost() {
        return getMethod() == METHOD.POST;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PUT
     */
    public boolean isPut() {
        return getMethod() == METHOD.PUT;
    }

    /**
     * helper method to check if the request content is Json
     *
     * @return true if Content-Type request header is application/json
     */
    public boolean isContentTypeJson() {
        return "application/json".equals(getContentType());
    }

    /**
     * helper method to check if the request content is Xm
     *
     * @return true if Content-Type request header is application/xml or
     * text/xml
     */
    public boolean isContentTypeXml() {
        return "text/xml".equals(getContentType())
                || "application/xml".equals(getContentType());
    }

    /**
     * helper method to check if the request content is text
     *
     * @return true if Content-Type request header starts with text/
     */
    public boolean isContentTypeText() {
        return getContentType() != null
                && getContentType().startsWith("text/");
    }

    public boolean isContentTypeFormOrMultipart() {
        return getContentType() != null
                && (getContentType().startsWith(FORM_URLENCODED)
                || getContentType().startsWith(MULTIPART));
    }

    public boolean isContentAvailable() {
        Field f;

        try {
            f = HttpServerExchange.class.getDeclaredField("BUFFERED_REQUEST_DATA");
            f.setAccessible(true);
        }
        catch (NoSuchFieldException | SecurityException ex) {
            throw new RuntimeException("could not find BUFFERED_REQUEST_DATA field", ex);
        }

        try {
            return null != getWrapped().getAttachment((AttachmentKey<PooledByteBuffer[]>) f.get(getWrapped()));
        }
        catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new RuntimeException("could not access BUFFERED_REQUEST_DATA field", ex);
        }
    }

    /**
     * @param srcs
     * @return
     * @throws IOException
     */
    private ByteBuffer readByteBuffer(final PooledByteBuffer[] srcs)
            throws IOException {
        if (srcs == null) {
            return null;
        }

        ByteBuffer dst = ByteBuffer.allocate(MAX_CONTENT_SIZE);

        int copied = 0;
        for (int i = 0; i < srcs.length; ++i) {
            PooledByteBuffer pooled = srcs[i];
            if (pooled != null) {
                final ByteBuffer buf = pooled.getBuffer();

                if (buf.remaining() > dst.remaining()) {
                    LOGGER.error("Request content exceeeded {} bytes limit",
                            MAX_CONTENT_SIZE);
                    throw new IOException("Request content exceeeded "
                            + MAX_CONTENT_SIZE + " bytes limit");
                }

                if (buf.hasRemaining()) {
                    copied += Buffers.copy(dst, buf);

                    // very important, I lost a day for this!
                    buf.flip();
                }
            }
        }

        return dst.flip();
    }

    /**
     * @return the wrapped
     */
    public HttpServerExchange getWrapped() {
        return wrapped;
    }
}
