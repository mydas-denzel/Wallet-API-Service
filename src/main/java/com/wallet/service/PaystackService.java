package com.wallet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.dtos.response.DepositResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaystackService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.paystack.secret-key}")
    private String secretKey;

    @Value("${app.paystack.base-url}")
    private String baseUrl;

    @Value("${app.paystack.webhook-secret}")
    private String webhookSecret;

    public DepositResponse initializeTransaction(String email, BigDecimal amount, String reference) {
        String url = baseUrl + "/transaction/initialize";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = String.format(
                "{\"email\": \"%s\", \"amount\": \"%s\", \"reference\": \"%s\"}",
                email,
                amount.multiply(BigDecimal.valueOf(100)).intValue(), // Convert to kobo
                reference
        );

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                boolean status = root.get("status").asBoolean();

                if (status) {
                    JsonNode data = root.get("data");
                    String authUrl = data.get("authorization_url").asText();
                    String ref = data.get("reference").asText();

                    return DepositResponse.builder()
                            .reference(ref)
                            .authorizationUrl(authUrl)
                            .build();
                }
            }

            throw new RuntimeException("Failed to initialize Paystack transaction");

        } catch (Exception e) {
            log.error("Paystack initialization failed: {}", e.getMessage());
            throw new RuntimeException("Paystack service error", e);
        }
    }

    public boolean verifyTransaction(String reference) {
        String url = baseUrl + "/transaction/verify/" + reference;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + secretKey);

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                boolean status = root.get("status").asBoolean();

                if (status) {
                    JsonNode data = root.get("data");
                    String txStatus = data.get("status").asText();
                    return "success".equals(txStatus);
                }
            }

            return false;

        } catch (Exception e) {
            log.error("Paystack verification failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            Mac sha512_HMAC = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecret.getBytes(), "HmacSHA512"
            );
            sha512_HMAC.init(secretKeySpec);

            byte[] hash = sha512_HMAC.doFinal(payload.getBytes());
            String calculatedSignature = Base64.getEncoder().encodeToString(hash);

            return calculatedSignature.equals(signature);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    public String generateReference() {
        return "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}