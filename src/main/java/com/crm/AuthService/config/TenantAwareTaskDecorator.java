package com.crm.AuthService.config;

import com.crm.AuthService.security.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * Propagates tenant context to async threads.
 * Critical for maintaining tenant isolation in @Async methods.
 */
@Slf4j
public class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        // Capture tenant context from parent thread
        Long tenantId = TenantContextHolder.getTenantId();
        String tenantSubdomain = TenantContextHolder.getTenantSubdomain();

        log.trace("Capturing tenant context for async task: tenantId={}", tenantId);

        return () -> {
            try {
                // Restore tenant context in child thread
                if (tenantId != null) {
                    TenantContextHolder.setTenantId(tenantId);
                    log.trace("Tenant context restored in async thread: tenantId={}", tenantId);
                }
                if (tenantSubdomain != null) {
                    TenantContextHolder.setTenantSubdomain(tenantSubdomain);
                }

                // Execute the actual task
                runnable.run();

            } finally {
                // Clean up tenant context
                TenantContextHolder.clear();
                log.trace("Tenant context cleared after async task completion");
            }
        };
    }
}