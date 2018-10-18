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
package io.uiam.security.handlers;

import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import io.uiam.Bootstrapper;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.RequestContext;
import static io.uiam.security.handlers.IAuthToken.AUTH_TOKEN_HEADER;
import static io.uiam.security.handlers.IAuthToken.AUTH_TOKEN_LOCATION_HEADER;
import static io.uiam.security.handlers.IAuthToken.AUTH_TOKEN_VALID_HEADER;
import io.uiam.security.impl.AuthTokenIdentityManager;
import io.uiam.security.impl.SimpleAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthTokenInjecterHandler extends PipedHttpHandler {
    private static final boolean enabled = Bootstrapper.getConfiguration().isAuthTokenEnabled();
    private static final long TTL = Bootstrapper.getConfiguration().getAuthTokenTtl();

    private static final Logger LOGGER
            = LoggerFactory.getLogger(AuthTokenInjecterHandler.class);

    private static SecureRandom RND_GENERATOR = new SecureRandom();

    /**
     * Creates a new instance of AuthTokenInjecterHandler
     *
     * @param next
     */
    public AuthTokenInjecterHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (enabled) {
            if (exchange.getSecurityContext() != null && exchange.getSecurityContext().isAuthenticated()) {
                Account authenticatedAccount = exchange.getSecurityContext().getAuthenticatedAccount();

                char[] token = cacheSessionToken(authenticatedAccount);

                injectTokenHeaders(exchange, token);
            }
        }

        next(exchange, context);
    }

    private void injectTokenHeaders(HttpServerExchange exchange, char[] token) {
        exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER, new String(token));
        exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER, Instant.now().plus(TTL, ChronoUnit.MINUTES).toString());
        exchange.getResponseHeaders().add(AUTH_TOKEN_LOCATION_HEADER, "/_authtokens/" + exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName());
    }

    private char[] cacheSessionToken(Account authenticatedAccount) {
        String id = authenticatedAccount.getPrincipal().getName();
        Optional<SimpleAccount> cachedTokenAccount 
                = AuthTokenIdentityManager.getInstance()
                        .getCachedAccounts().get(id);

        if (cachedTokenAccount == null) {
            char[] token = nextToken();
            SimpleAccount newCachedTokenAccount = new SimpleAccount(
                    id,
                    token,
                    authenticatedAccount.getRoles());

            AuthTokenIdentityManager.getInstance()
                    .getCachedAccounts()
                    .put(id, newCachedTokenAccount);

            return token;
        } else {
            return cachedTokenAccount.get().getCredentials().getPassword();
        }
    }

    private static char[] nextToken() {
        return new BigInteger(256, RND_GENERATOR)
                .toString(Character.MAX_RADIX)
                .toCharArray();
    }
}
