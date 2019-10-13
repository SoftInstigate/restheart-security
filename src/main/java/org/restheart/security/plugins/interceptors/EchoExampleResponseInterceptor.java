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
package org.restheart.security.plugins.interceptors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.restheart.security.handlers.exchange.JsonResponse;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.restheart.security.plugins.ResponseInterceptor;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class EchoExampleResponseInterceptor implements ResponseInterceptor {

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var response = JsonResponse.wrap(exchange);

        exchange.getResponseHeaders().add(HttpString.tryFromString("header"),
                "added by EchoExampleResponseInterceptor " + exchange.getRequestPath());

        if (response.isContentAvailable()) {
            JsonElement _content = response
                    .readContent();

            // can be null
            if (_content.isJsonObject()) {
                JsonObject content = _content
                        .getAsJsonObject();

                content.addProperty("prop2",
                        "property added by EchoExampleResponseInterceptor");

                response.writeContent(content);
            }
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return exchange.getRequestPath().equals("/iecho")
                || exchange.getRequestPath().equals("/siecho")
                || exchange.getRequestPath().equals("/anything");
    }

    @Override
    public boolean requiresResponseContent() {
        return true;
    }
}
