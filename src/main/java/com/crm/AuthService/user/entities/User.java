package com.crm.AuthService.user.entities;

import com.crm.AuthService.auth.entities.TenantAwareEntity;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User entity with tenant isolation.
 * Extends TenantAwareEntity to automatically handle tenant_id and filtering.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends TenantAwareEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean accountNonExpired = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean accountNonLocked = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean credentialsNonExpired = true;

    /**
     * Store role IDs (references to global roles table).
     * This is an element collection, not a full @ManyToMany.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role_id")
    @Builder.Default
    private Set<Long> roleIds = new HashSet<>();

    /**
     * Transient fields loaded at runtime from UserDetailsService.
     * These are NOT persisted to the database.
     */
    @Transient
    @Builder.Default
    private Set<String> roleNames = new HashSet<>();

    @Transient
    private String tenantName;

    @Transient
    private String tenantStatus;

    @Transient
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    // ============================================================
    // UserDetails Implementation
    // ============================================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roleNames.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    public void addRole(Long roleId) {
        if (this.roleIds == null) {
            this.roleIds = new HashSet<>();
        }
        this.roleIds.add(roleId);
    }

    public void removeRole(Long roleId) {
        if (this.roleIds != null) {
            this.roleIds.remove(roleId);
        }
    }

    public boolean hasRole(String roleName) {
        return roleNames != null && roleNames.contains(roleName);
    }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", enabled=" + enabled +
                ", roleCount=" + (roleIds != null ? roleIds.size() : 0) +
                ", tenantId=" + getTenantId() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}