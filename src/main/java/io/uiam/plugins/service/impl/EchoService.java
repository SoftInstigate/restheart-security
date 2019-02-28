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
package io.uiam.plugins.service.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.uiam.handlers.exchange.JsonRequest;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.exchange.ByteArrayRequest;
import io.uiam.handlers.exchange.JsonResponse;
import io.uiam.handlers.exchange.Request;
import io.uiam.plugins.service.PluggableService;
import io.uiam.utils.BuffersUtils;
import io.uiam.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class EchoService extends PluggableService {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(EchoService.class);

    /**
     *
     * @param next
     * @param name
     * @param uri
     * @param secured
     */
    public EchoService(PipedHttpHandler next, String name, String uri, Boolean secured) {
        super(next, name, uri, secured, null);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        JsonObject resp = new JsonObject();

        resp.addProperty("method", exchange.getRequestMethod().toString());
        resp.addProperty("URL", exchange.getRequestURL());

        if (Request.isContentTypeJson(exchange)) {
            var request = JsonRequest.wrap(exchange);

            try {
                resp.add("content", request.readContent());
            }
            catch (JsonSyntaxException jse) {
                resp.addProperty("content", getTruncatedContent(
                        ByteArrayRequest.wrap(exchange)));
                resp.addProperty("note",
                        "showing up to 20 bytes of the request content");
            }
        } else {
            var request = ByteArrayRequest.wrap(exchange);
            if (request.isContentTypeXml() || request.isContentTypeText()) {
                resp.addProperty("content", BuffersUtils.toString(request.getRawContent(),
                        Charset.forName("utf-8")));
            } else if (request.isContentAvailable()) {
                resp.addProperty("content", getTruncatedContent(request));
                resp.addProperty("note",
                        "showing up to 20 bytes of the request content");
            }
        }

        var response = JsonResponse.wrap(exchange);

        response.setStatusCode(HttpStatus.SC_OK);

        var qparams = new JsonObject();
        resp.add("qparams", qparams);

        exchange.getQueryParameters().forEach((name, values) -> {
            var _values = new JsonArray();

            qparams.add(name, _values);

            values.iterator().forEachRemaining(value -> {
                _values.add(value);
            });
        });

        var headers = new JsonObject();
        resp.add("headers", headers);

        exchange.getRequestHeaders().forEach(header -> {
            var _values = new JsonArray();
            headers.add(header.getHeaderName().toString(), _values);

            header.iterator().forEachRemaining(value -> {
                _values.add(value);
            });

        });

        response.writeContent(resp);
    }

    private String getTruncatedContent(ByteArrayRequest request)
            throws IOException {
        byte[] content = request.readContent();

        if (content == null) {
            return null;
        } else if (content.length < 1024) {
            return new String(content, StandardCharsets.UTF_8);
        } else {
            return new String(Arrays.copyOfRange(content, 0, 1023), StandardCharsets.UTF_8);
        }
    }

}
