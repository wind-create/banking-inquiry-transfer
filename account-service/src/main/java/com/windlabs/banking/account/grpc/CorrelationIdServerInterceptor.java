package com.windlabs.banking.account.grpc;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIdServerInterceptor
        implements ServerInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(
                    CorrelationIdServerInterceptor.class
            );

    private static final String MDC_KEY =
            "correlationId";

    private static final Metadata.Key<String>
            CORRELATION_ID_KEY =
            Metadata.Key.of(
                    "x-correlation-id",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile(
                    "^[A-Za-z0-9._:-]{1,128}$"
            );

    @Override
    public <ReqT, RespT>
    ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {

        String correlationId =
                resolveCorrelationId(
                        headers.get(
                                CORRELATION_ID_KEY
                        )
                );

        ServerCall<ReqT, RespT> wrappedCall =
                new ForwardingServerCall
                        .SimpleForwardingServerCall<>(
                                call
                        ) {

            @Override
            public void sendHeaders(
                    Metadata responseHeaders
            ) {

                responseHeaders.put(
                        CORRELATION_ID_KEY,
                        correlationId
                );

                super.sendHeaders(
                        responseHeaders
                );
            }
        };

        String previous =
                MDC.get(MDC_KEY);

        MDC.put(
                MDC_KEY,
                correlationId
        );

        ServerCall.Listener<ReqT> delegate;

        try {

            /*
             * correlationId TIDAK ditambahkan menggunakan
             * addKeyValue karena sudah ada di MDC.
             */
            log.atInfo()
                    .addKeyValue(
                            "grpc.method",
                            call.getMethodDescriptor()
                                    .getFullMethodName()
                    )
                    .log(
                            "Account gRPC request started"
                    );

            delegate =
                    next.startCall(
                            wrappedCall,
                            headers
                    );

        } finally {

            restoreMdc(previous);
        }

        return new ForwardingServerCallListener
                .SimpleForwardingServerCallListener<>(
                        delegate
                ) {

            @Override
            public void onMessage(
                    ReqT message
            ) {

                withCorrelationId(
                        correlationId,
                        () -> super.onMessage(
                                message
                        )
                );
            }

            @Override
            public void onHalfClose() {

                withCorrelationId(
                        correlationId,
                        super::onHalfClose
                );
            }

            @Override
            public void onCancel() {

                withCorrelationId(
                        correlationId,
                        super::onCancel
                );
            }

            @Override
            public void onComplete() {

                withCorrelationId(
                        correlationId,
                        () -> {

                            /*
                             * Jangan addKeyValue correlationId di sini.
                             * MDC sudah memasukkannya ke structured log.
                             */
                            log.atInfo()
                                    .addKeyValue(
                                            "grpc.method",
                                            call.getMethodDescriptor()
                                                    .getFullMethodName()
                                    )
                                    .log(
                                            "Account gRPC request completed"
                                    );

                            super.onComplete();
                        }
                );
            }

            @Override
            public void onReady() {

                withCorrelationId(
                        correlationId,
                        super::onReady
                );
            }
        };
    }

    private void withCorrelationId(
            String correlationId,
            Runnable runnable
    ) {

        String previous =
                MDC.get(MDC_KEY);

        MDC.put(
                MDC_KEY,
                correlationId
        );

        try {

            runnable.run();

        } finally {

            restoreMdc(previous);
        }
    }

    private void restoreMdc(
            String previous
    ) {

        if (previous == null) {

            MDC.remove(
                    MDC_KEY
            );

        } else {

            MDC.put(
                    MDC_KEY,
                    previous
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