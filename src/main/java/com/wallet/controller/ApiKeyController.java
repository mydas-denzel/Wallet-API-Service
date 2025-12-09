package com.wallet.controller;

import com.wallet.dtos.request.RolloverApiKeyRequest;
import com.wallet.dtos.response.ApiResponse;
import com.wallet.dtos.request.CreateApiKeyRequest;
import com.wallet.dtos.response.CreateApiKeyResponse;
import com.wallet.entity.User;
import com.wallet.enums.ExpiryDuration;
import com.wallet.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/keys")
@RequiredArgsConstructor
@Slf4j
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> createApiKey(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateApiKeyRequest request) {

        CreateApiKeyResponse response = apiKeyService.createApiKey(user, request);

        log.info("API key created for user: {}", user.getEmail());

        return ResponseEntity.ok(ApiResponse.success(
                "API key created successfully", response
        ));
    }

    @PostMapping("/rollover")
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> rolloverApiKey(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RolloverApiKeyRequest request) {

        CreateApiKeyResponse response = apiKeyService.rolloverApiKey(
                user, request.getExpiredKeyId(), request.getExpiry()
        );

        log.info("API key rolled over for user: {}", user.getEmail());

        return ResponseEntity.ok(ApiResponse.success(
                "API key rolled over successfully", response
        ));
    }
}