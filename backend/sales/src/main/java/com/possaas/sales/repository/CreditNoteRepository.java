package com.possaas.sales.repository;

import com.possaas.sales.domain.CreditNote;
import com.possaas.sales.domain.CreditNoteStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    List<CreditNote> findByCustomerIdAndStatusInOrderByIssuedAtDesc(
            UUID customerId, Collection<CreditNoteStatus> statuses);

    List<CreditNote> findByCustomerIdOrderByIssuedAtDesc(UUID customerId);

    List<CreditNote> findByStatusInOrderByIssuedAtDesc(Collection<CreditNoteStatus> statuses);

    List<CreditNote> findAllByOrderByIssuedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreditNote c WHERE c.id = :id")
    Optional<CreditNote> findByIdForUpdate(@Param("id") UUID id);
}
