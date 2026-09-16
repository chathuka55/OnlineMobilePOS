package com.possaas.tenancy.service;

import com.possaas.common.tenant.TenantContext;
import com.possaas.tenancy.domain.SettingKeys;
import com.possaas.tenancy.domain.TenantSetting;
import com.possaas.tenancy.repository.TenantSettingRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Typed access to per-tenant configuration.
 *
 * <p>Resolution order is outlet override, then tenant-wide value, then the built-in
 * default from {@link SettingKeys}. Values are read on nearly every sale, so they are
 * cached in Redis and evicted on write.
 */
@Service
public class SettingsService {

    private final TenantSettingRepository repository;

    public SettingsService(TenantSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "settings", key = "#root.methodName + ':' + T(com.possaas.common.tenant.TenantContext).requireTenantId() + ':' + #key")
    public String getString(String key) {
        return resolve(key).orElseGet(() -> SettingKeys.defaultValue(key));
    }

    @Transactional(readOnly = true)
    public String getString(String key, String fallback) {
        String value = getString(key);
        return value == null ? fallback : value;
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(getString(key));
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int fallback) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key, BigDecimal fallback) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** All effective settings, defaults included, for the settings screen. */
    @Transactional(readOnly = true)
    public Map<String, String> effectiveSettings() {
        Map<String, String> result = new LinkedHashMap<>();
        repository.findByOutletIdIsNull()
                .forEach(setting -> result.put(setting.getSettingKey(), setting.getSettingValue()));

        UUID outletId = TenantContext.current()
                .map(TenantContext.Scope::outletId)
                .orElse(null);
        if (outletId != null) {
            repository.findByOutletId(outletId)
                    .forEach(setting -> result.put(setting.getSettingKey(), setting.getSettingValue()));
        }
        return result;
    }

    @Transactional
    @CacheEvict(cacheNames = "settings", allEntries = true)
    public void put(String key, String value, TenantSetting.ValueType type) {
        TenantSetting setting = repository.findBySettingKeyAndOutletIdIsNull(key)
                .orElseGet(() -> TenantSetting.of(key, value, type, null));
        setting.setSettingValue(value);
        setting.setValueType(type);
        repository.save(setting);
    }

    @Transactional
    @CacheEvict(cacheNames = "settings", allEntries = true)
    public void putAll(Map<String, String> values) {
        values.forEach((key, value) -> put(key, value, inferType(value)));
    }

    private Optional<String> resolve(String key) {
        UUID outletId = TenantContext.current()
                .map(TenantContext.Scope::outletId)
                .orElse(null);

        if (outletId != null) {
            Optional<String> outletValue = repository
                    .findBySettingKeyAndOutletId(key, outletId)
                    .map(TenantSetting::getSettingValue);
            if (outletValue.isPresent()) {
                return outletValue;
            }
        }
        return repository.findBySettingKeyAndOutletIdIsNull(key)
                .map(TenantSetting::getSettingValue);
    }

    private static TenantSetting.ValueType inferType(String value) {
        if (value == null) {
            return TenantSetting.ValueType.STRING;
        }
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return TenantSetting.ValueType.BOOLEAN;
        }
        if (trimmed.matches("-?\\d+")) {
            return TenantSetting.ValueType.INTEGER;
        }
        if (trimmed.matches("-?\\d+\\.\\d+")) {
            return TenantSetting.ValueType.DECIMAL;
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return TenantSetting.ValueType.JSON;
        }
        return TenantSetting.ValueType.STRING;
    }
}
