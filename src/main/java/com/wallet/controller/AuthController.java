package com.wallet.controller;

import com.wallet.dtos.response.ApiResponse;
import com.wallet.dtos.response.AuthResponse;
import com.wallet.entity.User;
import com.wallet.security.JwtService;
import com.wallet.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtService jwtService;
    private final UserService userService;

    @GetMapping("/google")
    public void redirectToGoogle(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }

    @GetMapping("/google/callback")
    public ResponseEntity<ApiResponse<AuthResponse>> googleCallback(
            Authentication authentication) {

        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
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
            String token = jwtService.generateToken(user);

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .user(userService.toDto(user))
                    .build();

            log.info("User authenticated: {}", email);

            return ResponseEntity.ok(ApiResponse.success(
                    "Authentication successful", response
            ));

        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Authentication failed: " + e.getMessage()));
        }
    }

/*
    // Alternative: Manual JWT endpoint for testing
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> manualLogin(
            @RequestParam String email) {

        try {
            // For testing purposes only - in production, use Google OAuth
            User user = (User) userService.loadUserByUsername(email);

            // Generate JWT token
            String token = jwtService.generateToken(user);

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .user(userService.toDto(user))
                    .build();

            return ResponseEntity.ok(ApiResponse.success(
                    "Login successful (for testing only)", response
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid credentials"));
        }
    }

 */
}