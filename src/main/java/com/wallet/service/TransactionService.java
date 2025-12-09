package com.wallet.service;

import com.wallet.dtos.TransactionDto;
import com.wallet.entity.Transaction;
import com.wallet.entity.User;
import com.wallet.enums.TransactionStatus;
import com.wallet.enums.TransactionType;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserService userService;

    @Transactional
    public Transaction createTransaction(User user, TransactionType type,
                                         BigDecimal amount, String reference,
                                         String paystackReference) {
        Transaction transaction = Transaction.builder()
                .user(user)
                .type(type)
                .amount(amount)
                .reference(reference)
                .paystackReference(paystackReference)
                .status(TransactionStatus.PENDING)
                .build();

        return transactionRepository.save(transaction);
    }

    @Transactional
    public void createPendingDeposit(User user, BigDecimal amount, String reference) {
        createTransaction(user, TransactionType.DEPOSIT, amount, reference, reference);
    }

    @Transactional
    public void saveTransaction(Transaction transaction) {
        transactionRepository.save(transaction);
    }

    public List<Transaction> getUserTransactions(User user) {
        return transactionRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public Transaction getTransactionByReference(String reference) {
        return transactionRepository.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    public Optional<Transaction> getTransactionByPaystackReference(String paystackReference) {
        return transactionRepository.findByPaystackReference(paystackReference);
    }

    public User getUserByWalletNumber(String walletNumber) {
        return userService.findByWalletNumber(walletNumber);
    }

    public TransactionDto toDto(Transaction transaction) {
        return TransactionDto.builder()
                .reference(transaction.getReference())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .createdAt(transaction.getCreatedAt())
                .senderWalletNumber(transaction.getSenderWalletNumber())
                .receiverWalletNumber(transaction.getReceiverWalletNumber())
                .build();
    }
}