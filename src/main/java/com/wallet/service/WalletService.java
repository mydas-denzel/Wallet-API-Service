package com.wallet.service;

import com.wallet.dtos.response.BalanceResponse;
import com.wallet.entity.Transaction;
import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.enums.TransactionStatus;
import com.wallet.enums.TransactionType;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    @Lazy
    private final WalletRepository walletRepository;

    @Lazy
    private final TransactionService transactionService;

    @Transactional
    public Wallet createWallet(User user) {
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .currency("NGN")
                .isActive(true)
                .build();

        return walletRepository.save(wallet);
    }

    public Wallet getWalletByUser(User user) {
        return walletRepository.findByUser(user)
                .orElseGet(() -> createWallet(user));
    }

    public BalanceResponse getBalance(User user) {
        Wallet wallet = getWalletByUser(user);
        return BalanceResponse.builder()
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .build();
    }

    @Transactional
    public Transaction deposit(User user, BigDecimal amount, String reference,
                               String paystackReference) {
        Wallet wallet = getWalletByUser(user);

        Transaction transaction = transactionService.createTransaction(
                user,
                TransactionType.DEPOSIT,
                amount,
                reference,
                paystackReference
        );

        try {
            // Update wallet balance
            wallet.setBalance(wallet.getBalance().add(amount));
            wallet.setLastTransactionAt(java.time.LocalDateTime.now());
            walletRepository.save(wallet);

            // Update transaction status
            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionService.saveTransaction(transaction);

            log.info("Deposit successful: User={}, Amount={}, Reference={}",
                    user.getId(), amount, reference);

        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionService.saveTransaction(transaction);
            throw e;
        }

        return transaction;
    }

    @Transactional
    public Transaction transfer(User sender, String receiverWalletNumber,
                                BigDecimal amount, String reference) {
        // Check if sender has sufficient balance
        Wallet senderWallet = getWalletByUser(sender);
        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        // Find receiver
        Wallet receiverWallet = walletRepository.findByWalletNumber(receiverWalletNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        User receiver = receiverWallet.getUser();


        // Create transaction records
        Transaction senderTransaction = transactionService.createTransaction(
                sender,
                TransactionType.TRANSFER,
                amount.negate(),
                reference,
                null
        );
        senderTransaction.setReceiverWalletNumber(receiverWalletNumber);

        Transaction receiverTransaction = transactionService.createTransaction(
                receiver,
                TransactionType.TRANSFER,
                amount,
                reference + "_R",
                null
        );
        receiverTransaction.setSenderWalletNumber(sender.getWalletNumber());

        try {
            // Deduct from sender
            senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
            senderWallet.setLastTransactionAt(java.time.LocalDateTime.now());
            walletRepository.save(senderWallet);

            // Add to receiver
            receiverWallet.setBalance(receiverWallet.getBalance().add(amount));
            receiverWallet.setLastTransactionAt(java.time.LocalDateTime.now());
            walletRepository.save(receiverWallet);

            // Update transaction statuses
            senderTransaction.setStatus(TransactionStatus.SUCCESS);
            receiverTransaction.setStatus(TransactionStatus.SUCCESS);
            transactionService.saveTransaction(senderTransaction);
            transactionService.saveTransaction(receiverTransaction);

            log.info("Transfer successful: From={}, To={}, Amount={}, Reference={}",
                    sender.getId(), receiver.getId(), amount, reference);

        } catch (Exception e) {
            senderTransaction.setStatus(TransactionStatus.FAILED);
            receiverTransaction.setStatus(TransactionStatus.FAILED);
            transactionService.saveTransaction(senderTransaction);
            transactionService.saveTransaction(receiverTransaction);
            throw e;
        }

        return senderTransaction;
    }
}