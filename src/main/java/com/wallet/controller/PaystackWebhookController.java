package com.wallet.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.entity.Transaction;
import com.wallet.enums.TransactionStatus;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.service.PaystackService;
import com.wallet.service.TransactionService;
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

@RestController
@RequestMapping("/wallet/paystack")
@RequiredArgsConstructor
@Slf4j
public class PaystackWebhookController {

    private final PaystackService paystackService;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            HttpServletRequest request,
            @RequestBody String payload) {

        try {
            // 1. Verify Paystack signature
            String signature = request.getHeader("x-paystack-signature");
            if (!paystackService.verifyWebhookSignature(payload, signature)) {
                log.warn("Invalid Paystack signature");
                return ResponseEntity.status(401).body("Invalid signature");
            }

            // 2. Immediately return 200 OK (As Paystack recommends)
            //    But still process in background
            ResponseEntity<String> response =
                    ResponseEntity.ok("{\"status\":true}");

            // 3. Process asynchronously (but we'll process inline here)
            processWebhook(payload);

            return response;

        } catch (Exception e) {
            log.error("Webhook processing failed: {}", e.getMessage(), e);
            return ResponseEntity.ok("{\"status\":true}"); // Still return 200
        }
    }

    private void processWebhook(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.get("event").asText();

            if (!"charge.success".equals(event)) {
                log.info("Ignoring non-success event: {}", event);
                return;
            }

            JsonNode data = root.get("data");
            String reference = data.get("reference").asText();

            BigDecimal amount = BigDecimal.valueOf(data.get("amount").asLong())
                    .divide(BigDecimal.valueOf(100));

            // 4. Retrieve your internal transaction using your "reference"
            Transaction tx;
            try {
                tx = transactionService.getTransactionByReference(reference);
            } catch (ResourceNotFoundException e) {
                log.error("Unknown reference received from Paystack: {}", reference);
                return;
            }

            // 5. Idempotency check
            if (tx.getStatus() == TransactionStatus.SUCCESS) {
                log.info("Transaction already processed: {}", reference);
                return;
            }

            // 6. Mark transaction successful and credit wallet via service
            transactionService.markDepositSuccessful(tx, amount);

            log.info("Paystack webhook processed successfully: {}, Amount={}", reference, amount);

        } catch (Exception ex) {
            log.error("Webhook processing error: {}", ex.getMessage(), ex);
        }
    }
}
