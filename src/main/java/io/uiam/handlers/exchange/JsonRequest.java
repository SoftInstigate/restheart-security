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
package io.uiam.handlers.exchange;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import static io.uiam.handlers.exchange.AbstractExchange.LOGGER;
import io.uiam.utils.BuffersUtils;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonRequest extends Request<JsonElement> {

    protected static final JsonParser PARSER = new JsonParser();

    protected JsonRequest(HttpServerExchange exchange) {
        super(exchange);
        LOGGER = LoggerFactory.getLogger(JsonRequest.class);
    }

    public static JsonRequest wrap(HttpServerExchange exchange) {
        return new JsonRequest(exchange);
    }

    /**
     * @return the content as Json
     * @throws java.io.IOException
     */
    @Override
    public JsonElement readContent()
            throws IOException {
        if (!isContentAvailable()) {
            return null;
        }

        if (getWrapped().getAttachment(getRawContentKey()) == null) {
            return JsonNull.INSTANCE;
        } else {
            try {
                return PARSER.parse(BuffersUtils.toString(getRawContent(),
                        StandardCharsets.UTF_8));
            }
            catch (JsonParseException ex) {
                // dump bufferd content
                BuffersUtils.dump("Error parsing content", getRawContent());

                throw new IOException("Error parsing json", ex);
            }
        }
    }

    @Override
    public void writeContent(JsonElement content) throws IOException {
        setContentTypeAsJson();
        if (content == null) {
            setRawContent(null);
            getWrapped().getRequestHeaders().remove(Headers.CONTENT_LENGTH);
        } else {
            PooledByteBuffer[] dest;
            if (isContentAvailable()) {
                dest = getRawContent();
            } else {
                dest = new PooledByteBuffer[MAX_BUFFERS];
                setRawContent(dest);
            }

            int copied = BuffersUtils.transfer(
                    ByteBuffer.wrap(content.toString().getBytes()),
                    dest,
                    wrapped);
            
            // updated request content length
            // this is not needed in Response.writeContent() since done
            // by ModificableContentSinkConduit.updateContentLenght();
            getWrapped().getRequestHeaders().put(Headers.CONTENT_LENGTH, copied);
        }
    }
}
