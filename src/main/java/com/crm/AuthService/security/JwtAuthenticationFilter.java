package com.crm.AuthService.security;

import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter with Hibernate tenant filtering.
 * Extracts tenant ID from JWT and enables Hibernate filter for automatic tenant isolation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final EntityManager entityManager;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            final String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            final String jwt = authHeader.substring(7);
            final String username;
            final Long tenantId;

            try {
                username = jwtService.extractUsername(jwt);
                tenantId = jwtService.extractTenantId(jwt);

                // Set tenant context FIRST
                if (tenantId != null) {
                    TenantContextHolder.setTenantId(tenantId);

                    // Enable Hibernate filter for automatic tenant isolation
                    Session session = entityManager.unwrap(Session.class);
                    session.enableFilter("tenantFilter")
                            .setParameter("tenantId", tenantId);

                    log.debug("Tenant filter enabled: tenantId={}", tenantId);
                }

                // Then authenticate user
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    if (jwtService.isTokenValid(jwt, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );

                        authToken.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );

                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        log.debug("User authenticated: username={}, tenantId={}", username, tenantId);
                    } else {
                        log.warn("Invalid JWT token for user: {}", username);
                    }
                }

            } catch (Exception e) {
                log.error("Error processing JWT token: {}", e.getMessage());
            }

            filterChain.doFilter(request, response);

        } finally {
            // CRITICAL: Always clear tenant context
            TenantContextHolder.clear();
            log.trace("Tenant context cleared after request");
        }
    }
}