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

import io.undertow.server.HttpServerExchange;
import org.restheart.security.plugins.ResponseInterceptor;
import static org.restheart.security.plugins.TokenManager.ACCESS_CONTROL_EXPOSE_HEADERS;

/**
 * helper interceptor to add token headers to Access-Control-Expose-Headers to
 * handle CORS request
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class TokenCORSResponseInterceptor implements ResponseInterceptor {

    private final String[] headers;

    public TokenCORSResponseInterceptor(String... headers) {
        this.headers = headers;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var hs = exchange
                .getResponseHeaders()
                .get(ACCESS_CONTROL_EXPOSE_HEADERS);
        
        if (hs == null || hs.isEmpty()) {
            exchange
                .getResponseHeaders()
                .put(ACCESS_CONTROL_EXPOSE_HEADERS, headers());
        } else {
            var v0 = hs.getFirst();
            
            for (var h : this.headers) {
                if (!v0.contains(h)) {
                    v0 = v0.concat(", ").concat(h);
                }
            }
            
            exchange
                .getResponseHeaders()
                .put(ACCESS_CONTROL_EXPOSE_HEADERS, v0);
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return true;
    }
    
    private String headers() {
        var ret = "";
        var first = true;
        
        for (var h : this.headers) {
            if (first) {
                ret = ret.concat(h);
                first = false;
            } else {
                ret = ret.concat(", ").concat(h);
            }
        }
        
        return ret;
    }
}
