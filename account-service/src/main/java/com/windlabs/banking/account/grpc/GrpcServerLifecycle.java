package com.windlabs.banking.account.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GrpcServerLifecycle {

    private static final Logger log =
            LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final AccountGrpcServiceImpl accountGrpcService;
    private final int grpcPort;

    private Server server;

    public GrpcServerLifecycle(
            AccountGrpcServiceImpl accountGrpcService,
            @Value("${app.grpc.server.port:9092}") int grpcPort
    ) {
        this.accountGrpcService = accountGrpcService;
        this.grpcPort = grpcPort;
    }

    @PostConstruct
    public void start() throws IOException {

        server = NettyServerBuilder
                .forPort(grpcPort)
                .addService(accountGrpcService)
                .build()
                .start();

        log.info(
                "Account gRPC server started on port {}",
                grpcPort
        );
    }

    @PreDestroy
    public void stop() {

        if (server != null) {
            log.info("Shutting down Account gRPC server");
            server.shutdown();
        }
    }
}