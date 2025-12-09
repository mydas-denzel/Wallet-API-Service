package com.wallet.dtos.request;

import com.wallet.enums.ApiKeyPermission;
import com.wallet.enums.ExpiryDuration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class CreateApiKeyRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotEmpty(message = "Permissions are required")
    private Set<ApiKeyPermission> permissions;

    @NotNull(message = "Expiry duration is required")
    private ExpiryDuration expiry;
}