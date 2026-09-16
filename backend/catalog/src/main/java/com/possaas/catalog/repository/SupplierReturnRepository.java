package com.possaas.catalog.repository;

import com.possaas.catalog.domain.SupplierReturn;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierReturnRepository extends JpaRepository<SupplierReturn, UUID> {

    Optional<SupplierReturn> findByReturnNumberIgnoreCase(String returnNumber);

    Page<SupplierReturn> findAllByOrderByReturnedAtDesc(Pageable pageable);
}
