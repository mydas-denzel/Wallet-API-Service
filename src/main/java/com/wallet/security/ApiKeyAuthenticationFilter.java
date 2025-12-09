package com.wallet.security;

import com.wallet.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String apiKey = request.getHeader("x-api-key");

        if (apiKey != null) {
            var apiKeyEntity = apiKeyService.validateApiKey(apiKey);
            if (apiKeyEntity != null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(
                        apiKeyEntity.getUser().getEmail()
                );

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                authToken.setDetails(apiKeyEntity.getPermissions());
                SecurityContextHolder.getContext().setAuthentication(authToken);

                // Update last used timestamp
                apiKeyService.updateLastUsed(apiKeyEntity);
            }
        }

        filterChain.doFilter(request, response);
    }
}