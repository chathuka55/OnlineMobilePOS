package com.possaas.catalog.repository;

import com.possaas.catalog.domain.SupplierReturnLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierReturnLineRepository extends JpaRepository<SupplierReturnLine, UUID> {

    List<SupplierReturnLine> findBySupplierReturnIdOrderByLineNumberAsc(UUID supplierReturnId);
}
