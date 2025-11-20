package com.crm.AuthService.role.services;

import com.crm.AuthService.role.entities.Permission;
import com.crm.AuthService.user.entities.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * UPDATED: Works with new Role structure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";

    public boolean hasPermission(String resource, String action) {
        User user = getCurrentUser();
        if (user == null) {
            log.warn("No authenticated user found when checking permission: {}:{}", resource, action);
            return false;
        }

        return hasPermission(user, resource, action);
    }

    public boolean hasPermission(User user, String resource, String action) {
        if (isSuperAdmin(user)) {
            log.debug("User {} is SUPER_ADMIN - permission granted", user.getEmail());
            return true;
        }

        Set<Permission> permissions = getUserPermissions(user);

        boolean hasPermission = permissions.stream()
                .anyMatch(p ->
                        p.getResource().equalsIgnoreCase(resource) &&
                                p.getAction().equalsIgnoreCase(action)
                );

        if (hasPermission) {
            log.debug("Permission granted: user={}, permission={}:{}",
                    user.getEmail(), resource, action);
        } else {
            log.warn("Permission denied: user={}, permission={}:{}",
                    user.getEmail(), resource, action);
        }

        return hasPermission;
    }

    public boolean hasAnyRole(String... roleNames) {
        User user = getCurrentUser();
        if (user == null) {
            return false;
        }

        for (String roleName : roleNames) {
            if (user.hasRole(roleName)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasRole(String roleName) {
        return hasAnyRole(roleName);
    }

    /**
     * UPDATED: Extract permissions from Role entities.
     */
    @Cacheable(value = "userPermissions", key = "#user.id")
    public Set<Permission> getUserPermissions(User user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toSet());
    }

    public boolean isSuperAdmin(User user) {
        return user.hasRole(SUPER_ADMIN_ROLE);
    }

    public boolean isSuperAdmin() {
        User user = getCurrentUser();
        return user != null && isSuperAdmin(user);
    }

    public boolean isTenantAdmin(User user) {
        return user.hasRole("ROLE_TENANT_ADMIN") || user.hasRole("ROLE_ADMIN");
    }

    public boolean isTenantAdmin() {
        User user = getCurrentUser();
        return user != null && isTenantAdmin(user);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof User) {
            return (User) principal;
        }

        return null;
    }

    public void requirePermission(String resource, String action) {
        if (!hasPermission(resource, action)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    String.format("Access denied: Missing permission %s:%s", resource, action)
            );
        }
    }
}