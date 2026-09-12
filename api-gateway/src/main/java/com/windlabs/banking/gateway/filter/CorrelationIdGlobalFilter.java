package com.windlabs.banking.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_NAME = "X-Correlation-Id";

    private static final Logger log =
            LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {

        String correlationId = resolveCorrelationId(
                exchange.getRequest()
                        .getHeaders()
                        .getFirst(HEADER_NAME)
        );

        ServerHttpRequest request =
                exchange.getRequest()
                        .mutate()
                        .headers(headers ->
                                headers.set(
                                        HEADER_NAME,
                                        correlationId
                                )
                        )
                        .build();

        exchange.getResponse()
                .getHeaders()
                .set(
                        HEADER_NAME,
                        correlationId
                );

        long started = System.nanoTime();

        log.atInfo()
                .addKeyValue("correlationId", correlationId)
                .addKeyValue(
                        "http.method",
                        request.getMethod().name()
                )
                .addKeyValue(
                        "http.path",
                        request.getURI().getPath()
                )
                .log("Gateway request started");

        ServerWebExchange mutatedExchange =
                exchange.mutate()
                        .request(request)
                        .build();

        return chain.filter(mutatedExchange)
                .doOnSuccess(ignored -> {

                    long durationMs =
                            TimeUnit.NANOSECONDS.toMillis(
                                    System.nanoTime() - started
                            );

                    var status =
                            exchange.getResponse()
                                    .getStatusCode();

                    log.atInfo()
                            .addKeyValue(
                                    "correlationId",
                                    correlationId
                            )
                            .addKeyValue(
                                    "http.method",
                                    request.getMethod().name()
                            )
                            .addKeyValue(
                                    "http.path",
                                    request.getURI().getPath()
                            )
                            .addKeyValue(
                                    "http.status",
                                    status == null
                                            ? 200
                                            : status.value()
                            )
                            .addKeyValue(
                                    "durationMs",
                                    durationMs
                            )
                            .log("Gateway request completed");
                })
                .doOnError(ex -> {

                    long durationMs =
                            TimeUnit.NANOSECONDS.toMillis(
                                    System.nanoTime() - started
                            );

                    log.atError()
                            .addKeyValue(
                                    "correlationId",
                                    correlationId
                            )
                            .addKeyValue(
                                    "http.method",
                                    request.getMethod().name()
                            )
                            .addKeyValue(
                                    "http.path",
                                    request.getURI().getPath()
                            )
                            .addKeyValue(
                                    "durationMs",
                                    durationMs
                            )
                            .setCause(ex)
                            .log("Gateway request failed");
                });
    }

    private String resolveCorrelationId(String incoming) {

        if (incoming != null
                && SAFE_CORRELATION_ID
                        .matcher(incoming)
                        .matches()) {

            return incoming;
        }

        return UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {

        /*
         * Jalankan sangat awal sebelum filter bisnis/rate-limit.
         */
        return -1000;
    }
}