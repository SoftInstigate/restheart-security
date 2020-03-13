package org.restheart.security.handlers.metrics;

import org.slf4j.MDC;

import java.util.Optional;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.restheart.security.Bootstrapper;
import org.restheart.security.handlers.PipedHttpHandler;
import org.restheart.security.handlers.exchange.ByteArrayResponse;

/**
 * Handler to write tracing headers to the logging MDC. Pick it up via the
 * default way with "%X{name}", e.g. "%X{x-b3-traceid}".
 */
public class TracingInstrumentationHandler extends PipedHttpHandler {
    public TracingInstrumentationHandler() {
    }
    
    public TracingInstrumentationHandler(final PipedHttpHandler next) {
        super(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        Bootstrapper.getConfiguration().getTraceHeaders()
                .forEach((traceIdHeader) -> {
                    Optional.ofNullable(exchange.getRequestHeaders()
                            .get(traceIdHeader))
                            .flatMap(x -> Optional.ofNullable(x.peekFirst()))
                            .ifPresent(value -> {
                                MDC.put(traceIdHeader, value);
                                ByteArrayResponse.wrap(exchange)
                                        .setMDCContext(MDC.getCopyOfContextMap());
                                exchange.getResponseHeaders()
                                        .put(HttpString
                                                .tryFromString(traceIdHeader),
                                                value);
                            });
                });

        if (!exchange.isResponseComplete() && getNext() != null) {
            next(exchange);
        }

        Bootstrapper.getConfiguration()
                .getTraceHeaders().forEach((traceIdHeader) -> {
                    MDC.remove(traceIdHeader);
                });
    }
}
