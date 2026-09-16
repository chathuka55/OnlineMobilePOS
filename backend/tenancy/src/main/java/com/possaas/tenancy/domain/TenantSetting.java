package com.possaas.tenancy.domain;

import com.possaas.common.jpa.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Typed configuration value.
 *
 * <p>A tenant-wide row has a null {@code outletId}; an outlet-specific row overrides it.
 * This is where the desktop app's loose files now live: {@code config.properties} (printer
 * name), {@code passcode_config.properties} (lock timeout) and {@code invoice_terms.txt}.
 */
@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
@NoArgsConstructor
public class TenantSetting extends TenantEntity {

    @Column(name = "outlet_id")
    private UUID outletId;

    @Column(name = "setting_key", nullable = false)
    private String settingKey;

    @Column(name = "setting_value")
    private String settingValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    private ValueType valueType = ValueType.STRING;

    public static TenantSetting of(String key, String value, ValueType type, UUID outletId) {
        TenantSetting setting = new TenantSetting();
        setting.settingKey = key;
        setting.settingValue = value;
        setting.valueType = type;
        setting.outletId = outletId;
        return setting;
    }

    public enum ValueType {
        STRING, INTEGER, DECIMAL, BOOLEAN, JSON
    }
}
