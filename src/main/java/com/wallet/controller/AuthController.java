package com.wallet.controller;

import com.wallet.dtos.response.ApiResponse;
import com.wallet.dtos.response.AuthResponse;
import com.wallet.entity.User;
import com.wallet.security.CustomUserDetails;
import com.wallet.security.JwtService;
import com.wallet.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtService jwtService;
    private final UserService userService;

    @GetMapping("/google")
    public ResponseEntity<ApiResponse<String>> googleAuth() {
        // This endpoint would redirect to Google OAuth
        // In a real implementation, this would be handled by Spring Security OAuth2
        return ResponseEntity.ok(ApiResponse.success(
                "Redirect to /oauth2/authorization/google"
        ));
    }

    @GetMapping("/google/callback")
    public ResponseEntity<ApiResponse<AuthResponse>> googleCallback(
            HttpServletRequest request,
            Authentication authentication) {

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid authentication"));
        }

        Map<String, Object> attributes = oauthToken.getPrincipal().getAttributes();

        String googleId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");

        // Find or create user
        User user = userService.findOrCreateUser(googleId, email, name, picture);

        // Generate JWT token
        String token = jwtService.generateToken(new CustomUserDetails(user));

        AuthResponse response = AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(userService.toDto(user))
                .build();

        log.info("User authenticated: {}", email);

        return ResponseEntity.ok(ApiResponse.success(
                "Authentication successful", response
        ));
    }
}