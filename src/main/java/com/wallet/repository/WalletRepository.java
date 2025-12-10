package com.wallet.repository;

import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, String> {
    Optional<Wallet> findByUser(User user);
    Optional<Wallet> findByUserId(String userId);

    Optional<Wallet> findByWalletNumber(String receiverWalletNumber);
}