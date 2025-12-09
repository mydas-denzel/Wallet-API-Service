package com.wallet.security;

import com.wallet.enums.ApiKeyPermission;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Set;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject,
                                 Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String permissionString = permission.toString();

        // JWT users have all permissions
        if (authentication.getCredentials() == null &&
                !(authentication.getDetails() instanceof Set)) {
            return true;
        }

        // API key users check their permissions
        if (authentication.getDetails() instanceof Set) {
            Set<ApiKeyPermission> permissions = (Set<ApiKeyPermission>) authentication.getDetails();
            return permissions.stream()
                    .anyMatch(p -> p.name().equalsIgnoreCase(permissionString));
        }

        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        return false; // Not used
    }
}