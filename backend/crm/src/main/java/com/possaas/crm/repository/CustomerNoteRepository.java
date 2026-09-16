package com.possaas.crm.repository;

import com.possaas.crm.domain.CustomerNote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerNoteRepository extends JpaRepository<CustomerNote, UUID> {

    List<CustomerNote> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
