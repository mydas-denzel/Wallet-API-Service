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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/keys")
@RequiredArgsConstructor
@Slf4j
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping("/create")
    public CreateApiKeyResponse createApiKey(@RequestBody CreateApiKeyRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof User)) {
            throw new RuntimeException("No authenticated user found");
        }

        User user = (User) auth.getPrincipal();
        return apiKeyService.createApiKey(user, request);
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