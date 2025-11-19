package com.crm.AuthService.auth.services;

import com.crm.AuthService.auth.dtos.TenantRegistrationRequest;
import com.crm.AuthService.exception.EmailAlreadyExistsException;
import com.crm.AuthService.exception.RoleNotFoundException;
import com.crm.AuthService.exception.TenantAlreadyExistsException;
import com.crm.AuthService.role.entities.Role;
import com.crm.AuthService.role.repositories.RoleRepository;
import com.crm.AuthService.security.TenantContextHolder;
import com.crm.AuthService.tenant.entities.Tenant;
import com.crm.AuthService.tenant.repository.TenantRepository;
import com.crm.AuthService.user.entities.User;
import com.crm.AuthService.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Simplified Tenant Registration Service.
 * No schema creation, no async provisioning - just create tenant and admin user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRegistrationServiceImpl implements TenantRegistrationService {

    private final AuthHelper authHelper;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    private static final String MASTER_TENANT_SUBDOMAIN = "admin";
    private static final String TENANT_STATUS_ACTIVE = "ACTIVE";

    @Override
    @Transactional
    public void registerTenant(TenantRegistrationRequest request) {
        log.info("========================================");
        log.info("Starting Tenant Registration: {}", request.getSubdomain());
        log.info("========================================");

        // STEP 1: Validate subdomain
        authHelper.validateSubdomain(request.getSubdomain());
        log.debug("✓ Subdomain format valid: {}", request.getSubdomain());

        if (tenantRepository.findBySubdomain(request.getSubdomain()).isPresent()) {
            log.warn("✗ Subdomain already exists: {}", request.getSubdomain());
            throw new TenantAlreadyExistsException(request.getSubdomain());
        }
        log.debug("✓ Subdomain available: {}", request.getSubdomain());

        // STEP 2: Create tenant
        Tenant newTenant = authHelper.buildTenant(request);
        newTenant.setStatus(TENANT_STATUS_ACTIVE);
        Tenant savedTenant = tenantRepository.save(newTenant);
        log.info("✓ Tenant created: id={}, subdomain={}", savedTenant.getId(), savedTenant.getSubdomain());

        // STEP 3: Set tenant context for user creation
        TenantContextHolder.setTenantId(savedTenant.getId());
        log.debug("✓ Tenant context set: {}", savedTenant.getId());

        try {
            // STEP 4: Check if email already exists in this tenant
            if (userRepository.findByEmail(request.getAdminEmail()).isPresent()) {
                log.warn("✗ Email already exists in tenant {}: {}",
                        savedTenant.getSubdomain(), request.getAdminEmail());
                throw new EmailAlreadyExistsException(request.getAdminEmail());
            }

            // STEP 5: Determine role based on subdomain
            String roleToAssign = MASTER_TENANT_SUBDOMAIN.equalsIgnoreCase(savedTenant.getSubdomain())
                    ? ROLE_SUPER_ADMIN
                    : ROLE_ADMIN;

            log.info("Assigning role '{}' to admin of tenant '{}'",
                    roleToAssign, savedTenant.getSubdomain());

            Role adminRole = roleRepository.findByName(roleToAssign)
                    .orElseThrow(() -> new RoleNotFoundException(
                            roleToAssign + " not found during provisioning"));

            // STEP 6: Create admin user
            User adminUser = authHelper.buildAdminUser(request, adminRole, savedTenant);
            User savedUser = userRepository.save(adminUser);
            log.info("✓ Admin user created: id={}, email={}", savedUser.getId(), savedUser.getEmail());

            log.info("========================================");
            log.info("✅ Tenant Registration Complete: {}", savedTenant.getSubdomain());
            log.info("   Tenant ID: {}", savedTenant.getId());
            log.info("   Admin Email: {}", savedUser.getEmail());
            log.info("   Role: {}", roleToAssign);
            log.info("========================================");

        } catch (Exception e) {
            log.error("Failed to create admin user for tenant: {}", savedTenant.getSubdomain(), e);
            // Rollback will happen automatically due to @Transactional
            throw e;
        } finally {
            TenantContextHolder.clear();
            log.debug("✓ Tenant context cleared");
        }
    }
}