package com.possaas.sales.repository;

import com.possaas.sales.domain.Shift;
import com.possaas.sales.domain.ShiftStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    Optional<Shift> findByOutletIdAndStatus(UUID outletId, ShiftStatus status);

    boolean existsByOutletIdAndStatus(UUID outletId, ShiftStatus status);

    @Query("select s from Shift s where (:status is null or s.status = :status)")
    Page<Shift> search(@Param("status") ShiftStatus status, Pageable pageable);
}
