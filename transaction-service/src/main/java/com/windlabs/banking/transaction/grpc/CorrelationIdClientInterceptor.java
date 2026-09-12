package com.windlabs.banking.transaction.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CorrelationIdClientInterceptor
        implements ClientInterceptor {

    private static final String MDC_KEY =
            "correlationId";

    private static final Metadata.Key<String>
            CORRELATION_ID_KEY =
            Metadata.Key.of(
                    "x-correlation-id",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    @Override
    public <ReqT, RespT>
    ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next
    ) {

        String correlationId =
                MDC.get(MDC_KEY);

        if (correlationId == null
                || correlationId.isBlank()) {

            correlationId =
                    UUID.randomUUID()
                            .toString();
        }

        String finalCorrelationId =
                correlationId;

        ClientCall<ReqT, RespT> delegate =
                next.newCall(
                        method,
                        callOptions
                );

        return new ForwardingClientCall
                .SimpleForwardingClientCall<>(
                        delegate
                ) {

            @Override
            public void start(
                    Listener<RespT> responseListener,
                    Metadata headers
            ) {

                headers.put(
                        CORRELATION_ID_KEY,
                        finalCorrelationId
                );

                super.start(
                        responseListener,
                        headers
                );
            }
        };
    }
}