package com.wallet.controller;

import com.wallet.dtos.*;
import com.wallet.dtos.response.*;
import com.wallet.dtos.request.*;
import com.wallet.entity.ApiKey;
import com.wallet.entity.Transaction;
import com.wallet.entity.User;
import com.wallet.enums.ApiKeyPermission;
import com.wallet.enums.TransactionStatus;
import com.wallet.security.ApiKeyAuthenticationFilter;
import com.wallet.service.PaystackService;
import com.wallet.service.TransactionService;
import com.wallet.service.UserService;
import com.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@Slf4j
public class WalletController {

    private final WalletService walletService;
    private final PaystackService paystackService;
    private final TransactionService transactionService;
    private final UserService userService;

    @PostMapping("/deposit")
    @PreAuthorize("@methodSecurityConfig.hasPermission('DEPOSIT')")
    public ResponseEntity<ApiResponse<DepositResponse>> deposit(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody DepositRequest request) {

        String reference = paystackService.generateReference();

        DepositResponse depositResponse = paystackService.initializeTransaction(
                user.getEmail(),
                request.getAmount(),
                reference
        );

        // Create pending transaction
        transactionService.createPendingDeposit(
                user,
                request.getAmount(),
                depositResponse.getReference()
        );

        log.info("Deposit initialized: User={}, Amount={}, Reference={}",
                user.getId(), request.getAmount(), reference);

        return ResponseEntity.ok(ApiResponse.success(
                "Deposit initialized", depositResponse
        ));
    }

    @GetMapping("/balance")
    @PreAuthorize("@methodSecurityConfig.hasPermission('READ')")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
            @AuthenticationPrincipal User user) {

        BalanceResponse balance = walletService.getBalance(user);

        return ResponseEntity.ok(ApiResponse.success(
                "Balance retrieved successfully", balance
        ));
    }

    @PostMapping("/transfer")
    @PreAuthorize("@methodSecurityConfig.hasPermission('TRANSFER')")
    public ResponseEntity<ApiResponse<String>> transfer(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TransferRequest request) {

        String reference = paystackService.generateReference();

        walletService.transfer(
                user,
                request.getWalletNumber(),
                request.getAmount(),
                reference
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Transfer completed successfully"
        ));
    }

    @GetMapping("/transactions")
    @PreAuthorize("@methodSecurityConfig.hasPermission('READ')")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> getTransactions(
            @AuthenticationPrincipal User user) {

        List<Transaction> transactions = transactionService.getUserTransactions(user);
        List<TransactionDto> transactionDtos = transactions.stream()
                .map(transactionService::toDto)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(
                "Transactions retrieved successfully", transactionDtos
        ));
    }

    @GetMapping("/deposit/{reference}/status")
    public ResponseEntity<ApiResponse<TransactionStatus>> getDepositStatus(
            @AuthenticationPrincipal User user,
            @PathVariable String reference) {

        Transaction transaction = transactionService.getTransactionByReference(reference);

        // Verify ownership
        if (!transaction.getUser().getId().equals(user.getId())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Transaction not found"));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Status retrieved", transaction.getStatus()
        ));
    }
}