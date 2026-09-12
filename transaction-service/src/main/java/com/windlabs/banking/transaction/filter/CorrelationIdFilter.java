package com.windlabs.banking.transaction.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME =
            "X-Correlation-Id";

    public static final String MDC_KEY =
            "correlationId";

    private static final Logger log =
            LoggerFactory.getLogger(
                    CorrelationIdFilter.class
            );

    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile(
                    "^[A-Za-z0-9._:-]{1,128}$"
            );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId =
                resolveCorrelationId(
                        request.getHeader(HEADER_NAME)
                );

        long started =
                System.nanoTime();

        /*
         * Simpan correlationId di MDC.
         *
         * Karena structured logging Spring Boot membaca MDC,
         * kita TIDAK perlu addKeyValue("correlationId", ...).
         */
        MDC.put(
                MDC_KEY,
                correlationId
        );

        /*
         * Kembalikan correlation ID yang sama ke client.
         */
        response.setHeader(
                HEADER_NAME,
                correlationId
        );

        try {

            log.atInfo()
                    .addKeyValue(
                            "http.method",
                            request.getMethod()
                    )
                    .addKeyValue(
                            "http.path",
                            request.getRequestURI()
                    )
                    .log(
                            "Transaction service request started"
                    );

            filterChain.doFilter(
                    request,
                    response
            );

        } finally {

            long durationMs =
                    TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - started
                    );

            log.atInfo()
                    .addKeyValue(
                            "http.method",
                            request.getMethod()
                    )
                    .addKeyValue(
                            "http.path",
                            request.getRequestURI()
                    )
                    .addKeyValue(
                            "http.status",
                            response.getStatus()
                    )
                    .addKeyValue(
                            "durationMs",
                            durationMs
                    )
                    .log(
                            "Transaction service request completed"
                    );

            /*
             * Sangat penting agar correlationId request
             * tidak bocor ke request berikutnya pada thread yang sama.
             */
            MDC.remove(
                    MDC_KEY
            );
        }
    }

    private String resolveCorrelationId(
            String incoming
    ) {

        if (incoming != null
                && SAFE_CORRELATION_ID
                        .matcher(incoming)
                        .matches()) {

            return incoming;
        }

        return UUID.randomUUID()
                .toString();
    }
}