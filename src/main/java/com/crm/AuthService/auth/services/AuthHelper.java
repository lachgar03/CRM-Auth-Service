package com.crm.AuthService.auth.services;

import com.crm.AuthService.auth.dtos.AuthResponse;
import com.crm.AuthService.auth.dtos.TenantRegistrationRequest;
import com.crm.AuthService.role.entities.Role;

import com.crm.AuthService.security.JwtService;
import com.crm.AuthService.security.TenantContextHolder;
import com.crm.AuthService.tenant.entities.Tenant;
import com.crm.AuthService.user.entities.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthHelper {
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;


    @Value("${jwt.expiration}")
    private Long jwtExpiration;
    private static final String TENANT_STATUS_ACTIVE = "ACTIVE";
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    private static final List<String> RESERVED_SUBDOMAINS = List.of(
             "api", "www", "app", "test", "staging", "prod", "production",
            "dev", "demo", "mail", "ftp", "cdn", "static", "assets"
    );



    public void validateUserAndTenantStatus(User user) {
        if (!user.isEnabled()) {
            throw new DisabledException("Compte utilisateur désactivé");
        }

        if (user.getTenantStatus() == null) {
            throw new IllegalStateException("Tenant status not loaded onto User principal");
        }

        if (!TENANT_STATUS_ACTIVE.equals(user.getTenantStatus())) {
            throw new DisabledException(
                    String.format("Le tenant '%s' est désactivé ou suspendu",
                            user.getTenantName()) // Use name from principal
            );
        }
    }


    public void validateSubdomain(String subdomain) {
        if (subdomain == null || subdomain.isBlank()) {
            throw new IllegalArgumentException("Le sous-domaine ne peut pas être vide");
        }

        if (subdomain.length() < 3 || subdomain.length() > 63) {
            throw new IllegalArgumentException(
                    "Le sous-domaine doit contenir entre 3 et 63 caractères"
            );
        }

        if (!subdomain.matches("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")) {
            throw new IllegalArgumentException(
                    "Le sous-domaine ne peut contenir que des lettres minuscules, " +
                            "chiffres et tirets (ne peut pas commencer ou finir par un tiret)"
            );
        }

        if (RESERVED_SUBDOMAINS.contains(subdomain.toLowerCase())) {
            throw new IllegalArgumentException(
                    String.format("Le sous-domaine '%s' est réservé", subdomain)
            );
        }
    }


    public Tenant buildTenant(TenantRegistrationRequest request) {
        return Tenant.builder()
                .name(request.getCompanyName())
                .subdomain(request.getSubdomain().toLowerCase())
                .subscription_plan(request.getSubscriptionPlan())
                .status(TENANT_STATUS_ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }


    public User buildAdminUser(TenantRegistrationRequest request, Role adminRole, Tenant tenant) {
        return User.builder()
                .firstName(request.getAdminFirstName())
                .lastName(request.getAdminLastName())
                .email(request.getAdminEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .roleIds(Set.of(adminRole.getId())) // FIXED: Use roleIds
                // .tenant(tenant) // REMOVED: User entity does not have this field
                .enabled(true)
                .build();
    }


    public AuthResponse buildAuthResponse(User user) {
        Long tenantId = user.getTenantId();
        if (tenantId == null) {
            tenantId = TenantContextHolder.getRequiredTenantId();
        }

        String accessToken = jwtService.generateToken(user, tenantId);
        String refreshToken = jwtService.generateRefreshToken(user, tenantId);

        return buildAuthResponse(user, accessToken, refreshToken);
    }


    public AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {

        Set<String> roleNames = user.getRoleNames();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(TOKEN_TYPE_BEARER)
                .expiresIn(jwtExpiration / 1000)
                .tenantId(user.getTenantId()) // Get from principal
                .username(user.getEmail())
                .roles(roleNames) // Get from principal
                .tenantName(user.getTenantName()) // Get from principal
                .build();
    }

}