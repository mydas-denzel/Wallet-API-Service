package com.wallet.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.dtos.response.ApiResponse;
import com.wallet.entity.Transaction;
import com.wallet.entity.User;
import com.wallet.enums.TransactionStatus;
import com.wallet.service.PaystackService;
import com.wallet.service.TransactionService;
import com.wallet.service.UserService;
import com.wallet.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequestMapping("/wallet/paystack")
@RequiredArgsConstructor
@Slf4j
public class PaystackWebhookController {

    private final PaystackService paystackService;
    private final TransactionService transactionService;
    private final WalletService walletService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<String>> handleWebhook(
            HttpServletRequest request,
            @RequestBody String payload) {

        try {
            // Verify signature
            String signature = request.getHeader("x-paystack-signature");
            if (!paystackService.verifyWebhookSignature(payload, signature)) {
                log.warn("Invalid webhook signature");
                return ResponseEntity.status(401)
                        .body(ApiResponse.error("Invalid signature"));
            }

            JsonNode root = objectMapper.readTree(payload);
            String event = root.get("event").asText();

            if ("charge.success".equals(event)) {
                JsonNode data = root.get("data");
                String reference = data.get("reference").asText();
                BigDecimal amount = BigDecimal.valueOf(data.get("amount").asLong())
                        .divide(BigDecimal.valueOf(100)); // Convert from kobo
                String customerEmail = data.get("customer").get("email").asText();

                // Find user by email
                Optional<User> userOpt = userService.findByEmail(customerEmail);
                if (userOpt.isEmpty()) {
                    log.error("User not found for email: {}", customerEmail);
                    return ResponseEntity.ok(ApiResponse.error("User not found"));
                }

                User user = userOpt.get();

                // Check if transaction already processed
                Optional<Transaction> existingTx =
                        transactionService.getTransactionByPaystackReference(reference);

                if (existingTx.isPresent() &&
                        existingTx.get().getStatus() == TransactionStatus.SUCCESS) {
                    // Idempotent - already processed
                    log.info("Transaction already processed: {}", reference);
                    return ResponseEntity.ok(ApiResponse.success("Already processed"));
                }

                // Credit wallet
                walletService.deposit(user, amount, reference, reference);

                log.info("Webhook processed successfully: {}", reference);
            }

            return ResponseEntity.ok(ApiResponse.success("Webhook processed"));

        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Webhook processing failed"));
        }
    }
}