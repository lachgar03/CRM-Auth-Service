package com.crm.AuthService.tenant.mappers;

import com.crm.AuthService.tenant.dtos.TenantDto;
import com.crm.AuthService.tenant.entities.Tenant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Tenant toEntity(TenantDto tenantDto);

    TenantDto toDto(Tenant tenant);
}