package com.crm.AuthService.user.entities;

import com.crm.AuthService.auth.entities.TenantAwareEntity;
import com.crm.AuthService.role.entities.Role;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
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
 * FIXED: Uses proper ManyToMany relationship for roles with DB constraints.
 */
@Slf4j
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email_tenant", columnList = "email,tenant_id")
})
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
     * FIXED: Proper ManyToMany relationship with DB constraints.
     * Uses EAGER loading to avoid lazy initialization issues.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "role_id", nullable = false),
            foreignKey = @ForeignKey(name = "fk_user_roles_user"),
            inverseForeignKey = @ForeignKey(name = "fk_user_roles_role")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

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
        // Use transient roleNames if populated, otherwise extract from roles
        if (roleNames != null && !roleNames.isEmpty()) {
            return roleNames.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
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

    /**
     * SIMPLIFIED: Don't try to update bidirectional relationship.
     * Just add the role to this user.
     */
    public void addRole(Role role) {
        if (this.roles == null) {
            this.roles = new HashSet<>();
        }
        this.roles.add(role);
    }

    /**
     * SIMPLIFIED: Don't try to update bidirectional relationship.
     * Just remove the role from this user.
     */
    public void removeRole(Role role) {
        if (this.roles != null) {
            this.roles.remove(role);
        }
    }

    public Set<Long> getRoleIds() {
        if (roles == null || roles.isEmpty()) {
            return new HashSet<>();
        }
        return roles.stream()
                .map(Role::getId)
                .collect(Collectors.toSet());
    }

    /**
     * FIXED: Removed log reference, added proper warning.
     */
    public void setRoleIds(Set<Long> roleIds) {
        // Note: This is a helper for backward compatibility
        // Actual role setting should use setRoles() with Role entities
        System.err.println("WARNING: setRoleIds() called - consider using setRoles() with Role entities instead");
    }

    public boolean hasRole(String roleName) {
        if (roleNames != null && !roleNames.isEmpty()) {
            return roleNames.contains(roleName);
        }
        return roles != null && roles.stream()
                .anyMatch(role -> role.getName().equals(roleName));
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
                ", roleCount=" + (roles != null ? roles.size() : 0) +
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