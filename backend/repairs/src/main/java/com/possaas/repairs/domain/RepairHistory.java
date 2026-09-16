package com.possaas.repairs.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "repair_status_history")
@Getter
@Setter
@NoArgsConstructor
public class RepairHistory extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repair_order_id", nullable = false)
    private RepairOrder repairOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private RepairOrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private RepairOrderStatus toStatus;

    @Column(name = "note")
    private String note;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @Column(name = "changed_by")
    private UUID changedBy;

    public static RepairHistory of(RepairOrderStatus from, RepairOrderStatus to, String note, UUID changedBy) {
        RepairHistory history = new RepairHistory();
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setNote(note);
        history.setChangedBy(changedBy);
        history.setChangedAt(Instant.now());
        return history;
    }
}
