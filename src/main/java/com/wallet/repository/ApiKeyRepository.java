package com.wallet.repository;

import com.wallet.entity.ApiKey;
import com.wallet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {
    Optional<ApiKey> findByKey(String key);
    Optional<ApiKey> findByKeyAndIsActive(String key, boolean isActive);
    List<ApiKey> findByUser(User user);
    long countByUserAndIsActive(User user, boolean isActive);
}