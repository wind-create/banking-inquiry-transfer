package com.windlabs.banking.account.grpc;

import com.windlabs.banking.account.service.AccountTransferPostingService;
import com.windlabs.banking.account.service.TransferPostingException;
import com.windlabs.banking.account.service.TransferPostingResult;
import com.windlabs.banking.grpc.account.AccountGrpcServiceGrpc;
import com.windlabs.banking.grpc.account.ExecuteTransferRequest;
import com.windlabs.banking.grpc.account.ExecuteTransferResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AccountGrpcServiceImpl
        extends AccountGrpcServiceGrpc.AccountGrpcServiceImplBase {

    private final AccountTransferPostingService transferService;

    public AccountGrpcServiceImpl(
            AccountTransferPostingService transferService
    ) {
        this.transferService = transferService;
    }

    @Override
    public void executeTransfer(
            ExecuteTransferRequest request,
            StreamObserver<ExecuteTransferResponse> responseObserver
    ) {

        try {

            TransferPostingResult result =
                    transferService.execute(
                            request.getCustomerId(),
                            request.getIdempotencyKey(),
                            request.getSourceAccountNumber(),
                            request.getDestinationAccountNumber(),
                            new BigDecimal(request.getAmount()),
                            request.getCurrency()
                    );

            ExecuteTransferResponse response =
                    ExecuteTransferResponse.newBuilder()
                            .setTransferId(
                                    result.transferId().toString()
                            )
                            .setSourceAccountNumber(
                                    result.sourceAccountNumber()
                            )
                            .setDestinationAccountNumber(
                                    result.destinationAccountNumber()
                            )
                            .setAmount(
                                    result.amount().toPlainString()
                            )
                            .setCurrency(result.currency())
                            .setSourceBalanceAfter(
                                    result.sourceBalanceAfter()
                                            .toPlainString()
                            )
                            .setDestinationBalanceAfter(
                                    result.destinationBalanceAfter()
                                            .toPlainString()
                            )
                            .setProcessedOn(
                                    result.processedOn().toString()
                            )
                            .setReplayed(result.replayed())
                            .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (NumberFormatException ex) {

            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Invalid amount")
                            .asRuntimeException()
            );

        } catch (TransferPostingException ex) {

            responseObserver.onError(
                    mapStatus(ex)
                            .withDescription(ex.getMessage())
                            .asRuntimeException()
            );

        } catch (Exception ex) {

            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(
                                    "Internal transfer processing error"
                            )
                            .asRuntimeException()
            );
        }
    }

    private Status mapStatus(
            TransferPostingException exception
    ) {

        return switch (exception.getCode()) {

            case INVALID_REQUEST ->
                    Status.INVALID_ARGUMENT;

            case ACCOUNT_NOT_FOUND ->
                    Status.NOT_FOUND;

            case SOURCE_ACCOUNT_NOT_OWNED ->
                    Status.PERMISSION_DENIED;

            case ACCOUNT_NOT_ACTIVE,
                 CURRENCY_MISMATCH,
                 INSUFFICIENT_BALANCE ->
                    Status.FAILED_PRECONDITION;

            case IDEMPOTENCY_CONFLICT ->
                    Status.ALREADY_EXISTS;
        };
    }
}