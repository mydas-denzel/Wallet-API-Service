package com.wallet.dtos.request;

import com.wallet.enums.ExpiryDuration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RolloverApiKeyRequest {

    @NotBlank(message = "Expired key ID is required")
    private String expiredKeyId;

    @NotNull(message = "Expiry duration is required")
    private ExpiryDuration expiry;
}