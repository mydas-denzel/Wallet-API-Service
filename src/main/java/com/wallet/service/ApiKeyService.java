package com.wallet.service;

import com.wallet.dtos.request.CreateApiKeyRequest;
import com.wallet.dtos.response.CreateApiKeyResponse;
import com.wallet.entity.ApiKey;
import com.wallet.entity.User;
import com.wallet.enums.ApiKeyPermission;
import com.wallet.enums.ExpiryDuration;
import com.wallet.exception.BadRequestException;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    @Value("${app.api-key.prefix:sk_live_}")
    private String apiKeyPrefix;

    @Value("${app.api-key.length:32}")
    private int apiKeyLength;

    @Value("${app.api-key.max-active-keys:5}")
    private int maxActiveKeys;

    @Transactional
    public CreateApiKeyResponse createApiKey(User user, CreateApiKeyRequest request) {
        // Check active key limit
        long activeKeyCount = apiKeyRepository.countByUserAndIsActive(user, true);
        if (activeKeyCount >= maxActiveKeys) {
            throw new BadRequestException(
                    String.format("Maximum %d active API keys allowed", maxActiveKeys)
            );
        }

        // Generate API key
        String apiKeyValue = generateApiKey();

        // Calculate expiry
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusHours(request.getExpiry().getHours());

        // Create API key entity
        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .key(apiKeyValue)
                .name(request.getName())
                .permissions(request.getPermissions())
                .expiresAt(expiresAt)
                .isActive(true)
                .build();

        apiKeyRepository.save(apiKey);

        log.info("API key created: User={}, Name={}", user.getId(), request.getName());

        return CreateApiKeyResponse.builder()
                .apiKey(apiKeyValue)
                .expiresAt(expiresAt)
                .build();
    }

    private String generateApiKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[apiKeyLength];
        random.nextBytes(bytes);

        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return apiKeyPrefix + key;
    }

    @Transactional
    public CreateApiKeyResponse rolloverApiKey(User user, String expiredKeyId,
                                               ExpiryDuration expiry) {
        // Find the expired key
        ApiKey expiredKey = apiKeyRepository.findById(expiredKeyId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found"));

        // Verify ownership
        if (!expiredKey.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("API key not owned by user");
        }

        // Verify it's expired
        if (!expiredKey.isExpired()) {
            throw new BadRequestException("API key is not expired");
        }

        // Deactivate old key
        expiredKey.setActive(false);
        apiKeyRepository.save(expiredKey);

        // Create new key with same permissions
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName(expiredKey.getName() + " (Rollover)");
        request.setPermissions(expiredKey.getPermissions());
        request.setExpiry(expiry);

        return createApiKey(user, request);
    }

    public ApiKey validateApiKey(String apiKeyValue) {
        return apiKeyRepository.findByKeyAndIsActive(apiKeyValue, true)
                .filter(key -> !key.isExpired())
                .orElse(null);
    }

    @Transactional
    public void updateLastUsed(ApiKey apiKey) {
        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKeyRepository.save(apiKey);
    }

    public List<ApiKey> getUserApiKeys(User user) {
        return apiKeyRepository.findByUser(user);
    }

    public boolean hasPermission(ApiKey apiKey, ApiKeyPermission permission) {
        return apiKey != null && apiKey.hasPermission(permission);
    }
}