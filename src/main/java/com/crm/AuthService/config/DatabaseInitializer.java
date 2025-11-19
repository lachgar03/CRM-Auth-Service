package com.crm.AuthService.config;

import com.crm.AuthService.auth.dtos.TenantRegistrationRequest;
import com.crm.AuthService.auth.services.TenantRegistrationService;
import com.crm.AuthService.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Simplified Database Initializer.
 * No schema migration needed - Flyway runs automatically.
 * Just ensures master tenant exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer {

    private final TenantRepository tenantRepository;
    private final TenantRegistrationService tenantRegistrationService;

    @Value("${app.super-admin.email}")
    private String adminEmail;

    @Value("${app.super-admin.password}")
    private String adminPassword;

    @Value("${app.super-admin.first-name}")
    private String adminFirstName;

    @Value("${app.super-admin.last-name}")
    private String adminLastName;

    private static final String MASTER_TENANT_SUBDOMAIN = "admin";

    /**
     * Runs after Spring Boot is fully started.
     * Checks if master tenant exists, creates it if not.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void initializeDatabase() {
        log.info("======================================");
        log.info("🚀 DATABASE INITIALIZATION STARTING");
        log.info("======================================");

        try {
            // Flyway has already run at this point (auto-configured by Spring Boot)
            log.info("✅ Flyway migration completed (auto-configured)");

            // Check and create master tenant if needed
            log.info("STEP: Checking master tenant...");
            bootstrapMasterTenant();

            log.info("======================================");
            log.info("✅ DATABASE INITIALIZATION COMPLETED");
            log.info("======================================");

        } catch (Exception e) {
            log.error("======================================");
            log.error("❌ DATABASE INITIALIZATION FAILED");
            log.error("======================================", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void bootstrapMasterTenant() {
        if (tenantRepository.findBySubdomain(MASTER_TENANT_SUBDOMAIN).isPresent()) {
            log.info("✓ Master tenant '{}' already exists. Skipping bootstrap.", MASTER_TENANT_SUBDOMAIN);
            return;
        }

        log.warn("⚠️  Master tenant '{}' NOT FOUND. Creating...", MASTER_TENANT_SUBDOMAIN);

        TenantRegistrationRequest request = new TenantRegistrationRequest();
        request.setCompanyName("CRM Master Admin");
        request.setSubdomain(MASTER_TENANT_SUBDOMAIN);
        request.setAdminEmail(adminEmail);
        request.setAdminPassword(adminPassword);
        request.setAdminFirstName(adminFirstName);
        request.setAdminLastName(adminLastName);
        request.setSubscriptionPlan("ENTERPRISE");

        try {
            tenantRegistrationService.registerTenant(request);
            log.info("✅ Master tenant created successfully");
            log.info("   Subdomain: {}", MASTER_TENANT_SUBDOMAIN);
            log.info("   Admin Email: {}", adminEmail);
        } catch (Exception e) {
            log.error("❌ Failed to create master tenant", e);
            throw new RuntimeException("Master tenant creation failed", e);
        }
    }
}