package com.crm.AuthService.auth.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;

    @Builder.Default  // Add this annotation
    private String tokenType = "Bearer";

    private Long expiresIn;
    private Long tenantId;
    private String username;
    private Set<String> roles;
    private String tenantName;
}