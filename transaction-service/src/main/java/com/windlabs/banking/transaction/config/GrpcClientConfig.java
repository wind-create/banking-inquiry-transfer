package com.windlabs.banking.transaction.config;

import com.windlabs.banking.grpc.account.AccountGrpcServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.windlabs.banking.transaction.grpc.CorrelationIdClientInterceptor;

import io.grpc.Channel;
import io.grpc.ClientInterceptors;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel accountGrpcChannel(
            @Value("${app.grpc.account.host}") String host,
            @Value("${app.grpc.account.port}") int port
    ) {
        return NettyChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public AccountGrpcServiceGrpc
            .AccountGrpcServiceBlockingStub
    accountGrpcStub(
            ManagedChannel accountGrpcChannel,
            CorrelationIdClientInterceptor
                    correlationIdClientInterceptor
    ) {

        Channel channel =
                ClientInterceptors.intercept(
                        accountGrpcChannel,
                        correlationIdClientInterceptor
                );

        return AccountGrpcServiceGrpc
                .newBlockingStub(
                        channel
                );
    }
}