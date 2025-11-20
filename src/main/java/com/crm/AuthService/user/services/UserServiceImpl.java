package com.crm.AuthService.user.services;

import com.crm.AuthService.cache.CacheEvictionService;
import com.crm.AuthService.exception.EmailAlreadyExistsException;
import com.crm.AuthService.exception.RoleNotFoundException;
import com.crm.AuthService.exception.TenantNotFoundException;
import com.crm.AuthService.exception.UserNotFoundException;
import com.crm.AuthService.role.entities.Role;
import com.crm.AuthService.role.repositories.RoleRepository;
import com.crm.AuthService.security.TenantContextHolder;
import com.crm.AuthService.tenant.entities.Tenant;
import com.crm.AuthService.tenant.repository.TenantRepository;
import com.crm.AuthService.user.dtos.*;
import com.crm.AuthService.user.entities.User;
import com.crm.AuthService.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheEvictionService cacheEvictionService;

    private Tenant getRequiredTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with id: " + tenantId));
    }

    /**
     * UPDATED: Validates role names and returns Role entities (not just IDs).
     */
    private Set<Role> validateAndGetRoles(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return new HashSet<>();
        }

        Set<Role> foundRoles = roleRepository.findAllByNameIn(roleNames);

        if (foundRoles.size() != roleNames.size()) {
            Set<String> foundNames = foundRoles.stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet());

            Set<String> missingNames = roleNames.stream()
                    .filter(name -> !foundNames.contains(name))
                    .collect(Collectors.toSet());

            throw new RoleNotFoundException("Les rôles suivants n'existent pas: " + missingNames);
        }

        return foundRoles;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable, String search) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Tenant tenant = getRequiredTenant(tenantId);

        Page<User> users;
        if (search != null && !search.isBlank()) {
            users = userRepository.findByTenantIdAndSearch(search, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return users.map(user -> toUserResponse(user, tenant));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Tenant tenant = getRequiredTenant(tenantId);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        return toUserResponse(user, tenant);
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Tenant tenant = getRequiredTenant(tenantId);

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        // UPDATED: Get Role entities instead of just IDs
        Set<Role> roles = validateAndGetRoles(request.getRoleNames());

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(roles) // Set Role entities
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User created: id={}, email={}, tenantId={}", savedUser.getId(), savedUser.getEmail(), tenantId);

        return toUserResponse(savedUser, tenant);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Tenant tenant = getRequiredTenant(tenantId);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        String oldEmail = user.getEmail();

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new EmailAlreadyExistsException(request.getEmail());
            }
            user.setEmail(request.getEmail().toLowerCase());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        // UPDATED: Set Role entities
        if (request.getRoleNames() != null) {
            Set<Role> roles = validateAndGetRoles(request.getRoleNames());
            user.setRoles(roles);
        }

        user.setUpdatedAt(LocalDateTime.now());
        User updatedUser = userRepository.save(user);

        cacheEvictionService.evictUserCaches(updatedUser.getId(), oldEmail);
        if (!oldEmail.equals(updatedUser.getEmail())) {
            cacheEvictionService.evictUserCaches(updatedUser.getId(), updatedUser.getEmail());
        }

        log.info("User updated: id={}, email={}, tenantId={}", updatedUser.getId(), updatedUser.getEmail(), tenantId);

        return toUserResponse(updatedUser, tenant);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        user.setEnabled(false);
        user.setAccountNonLocked(false);
        userRepository.save(user);
        cacheEvictionService.evictUserCaches(user.getId(), user.getEmail());

        log.info("User soft deleted: id={}, email={}, tenantId={}", user.getId(), user.getEmail(), tenantId);
    }

    @Override
    @Transactional
    public UserResponse activateUser(Long id) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Tenant tenant = getRequiredTenant(tenantId);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        user.setEnabled(true);
        user.setAccountNonLocked(true);
        User savedUser = userRepository.save(user);
        cacheEvictionService.evictUserCaches(savedUser.getId(), savedUser.getEmail());

        log.info("User activated: id={}, email={}", savedUser.getId(), savedUser.getEmail());

        return toUserResponse(savedUser, tenant);
    }

    @Override
    @Transactional
    public UserResponse deactivateUser(Long id) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Tenant tenant = getRequiredTenant(tenantId);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        user.setEnabled(false);
        user.setAccountNonLocked(false);
        User savedUser = userRepository.save(user);

        cacheEvictionService.evictUserCaches(savedUser.getId(), savedUser.getEmail());
        log.info("User deactivated: id={}, email={}", savedUser.getId(), savedUser.getEmail());

        return toUserResponse(savedUser, tenant);
    }

    @Override
    @Transactional
    public UserResponse assignRoles(Long id, Set<String> roleNames) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Tenant tenant = getRequiredTenant(tenantId);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        // UPDATED: Set Role entities
        Set<Role> validatedRoles = validateAndGetRoles(roleNames);
        user.setRoles(validatedRoles);
        User savedUser = userRepository.save(user);

        cacheEvictionService.evictUserCaches(savedUser.getId(), savedUser.getEmail());

        log.info("Roles assigned: userId={}, roleNames={}", savedUser.getId(), roleNames);

        return toUserResponse(savedUser, tenant);
    }

    private UserResponse toUserResponse(User user, Tenant tenant) {
        // UPDATED: Extract roles directly from User entity
        Set<RoleDto> roleDtos = user.getRoles().stream()
                .map(role -> RoleDto.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .description(role.getDescription())
                        .build())
                .collect(Collectors.toSet());

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .roles(roleDtos)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}