package com.crm.AuthService.security;

import com.crm.AuthService.exception.TenantNotFoundException;
import com.crm.AuthService.tenant.entities.Tenant;
import com.crm.AuthService.tenant.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves tenant from headers BEFORE authentication.
 * This filter runs before JwtAuthenticationFilter to set tenant context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String TENANT_SUBDOMAIN_HEADER = "X-Tenant-Subdomain";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Skip tenant resolution for public endpoints
            String path = request.getRequestURI();
            if (isPublicEndpoint(path)) {
                log.trace("Skipping tenant resolution for public endpoint: {}", path);
                filterChain.doFilter(request, response);
                return;
            }

            // Try to resolve tenant from headers
            Long tenantId = resolveTenantFromHeaders(request);

            if (tenantId != null) {
                TenantContextHolder.setTenantId(tenantId);
                log.debug("Tenant context set from headers: tenantId={}", tenantId);
            }

            filterChain.doFilter(request, response);

        } catch (TenantNotFoundException e) {
            log.error("Tenant resolution failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Invalid tenant\", \"message\": \"" + e.getMessage() + "\"}");
            response.setContentType("application/json");
        } finally {
            // Don't clear context here - let JwtAuthenticationFilter handle it
            // This allows the context to flow through the entire request
        }
    }

    private Long resolveTenantFromHeaders(HttpServletRequest request) {
        // Priority 1: X-Tenant-ID header
        String tenantIdHeader = request.getHeader(TENANT_ID_HEADER);
        if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
            try {
                Long tenantId = Long.parseLong(tenantIdHeader);
                // Validate tenant exists
                if (tenantRepository.findById(tenantId).isPresent()) {
                    return tenantId;
                }
                throw new TenantNotFoundException("Tenant not found with ID: " + tenantId);
            } catch (NumberFormatException e) {
                throw new TenantNotFoundException("Invalid tenant ID format: " + tenantIdHeader);
            }
        }

        // Priority 2: X-Tenant-Subdomain header
        String subdomain = request.getHeader(TENANT_SUBDOMAIN_HEADER);
        if (subdomain != null && !subdomain.isBlank()) {
            Tenant tenant = tenantRepository.findBySubdomain(subdomain.toLowerCase())
                    .orElseThrow(() -> new TenantNotFoundException("Tenant not found with subdomain: " + subdomain));
            return tenant.getId();
        }

        // Priority 3: Extract from subdomain in Host header (e.g., acme.crm.com)
        String host = request.getHeader("Host");
        if (host != null && host.contains(".")) {
            String possibleSubdomain = host.split("\\.")[0];
            return tenantRepository.findBySubdomain(possibleSubdomain.toLowerCase())
                    .map(Tenant::getId)
                    .orElse(null);
        }

        return null;
    }

    private boolean isPublicEndpoint(String path) {
        return path.equals("/api/v1/auth/register") ||
                path.equals("/api/v1/auth/health") ||
                path.startsWith("/actuator/") ||
                path.startsWith("/swagger-ui/") ||
                path.startsWith("/v3/api-docs/");
    }
}