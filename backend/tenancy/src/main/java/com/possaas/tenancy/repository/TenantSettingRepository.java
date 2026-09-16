package com.possaas.tenancy.repository;

import com.possaas.tenancy.domain.TenantSetting;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingRepository extends JpaRepository<TenantSetting, UUID> {

    Optional<TenantSetting> findBySettingKeyAndOutletIdIsNull(String settingKey);

    Optional<TenantSetting> findBySettingKeyAndOutletId(String settingKey, UUID outletId);

    List<TenantSetting> findByOutletIdIsNull();

    List<TenantSetting> findByOutletId(UUID outletId);
}
