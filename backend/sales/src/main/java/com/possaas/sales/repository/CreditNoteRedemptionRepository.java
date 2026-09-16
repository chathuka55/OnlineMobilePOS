package com.possaas.sales.repository;

import com.possaas.sales.domain.CreditNoteDocumentType;
import com.possaas.sales.domain.CreditNoteRedemption;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditNoteRedemptionRepository extends JpaRepository<CreditNoteRedemption, UUID> {

    List<CreditNoteRedemption> findByDocumentTypeAndDocumentIdAndReversedAtIsNull(
            CreditNoteDocumentType documentType, UUID documentId);

    List<CreditNoteRedemption> findByCreditNoteIdOrderByRedeemedAtDesc(UUID creditNoteId);
}
