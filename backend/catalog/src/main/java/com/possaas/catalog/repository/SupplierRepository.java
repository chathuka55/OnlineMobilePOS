package com.possaas.catalog.repository;

import com.possaas.catalog.domain.Supplier;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Supplier> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    @Query("""
            select s from Supplier s
             where s.deletedAt is null
               and (:activeOnly = false or s.active = true)
               and (
                    :q is null or :q = ''
                    or lower(s.name) like lower(concat('%', cast(:q as string), '%'))
                    or lower(s.code) like lower(concat('%', cast(:q as string), '%'))
                    or s.phonePrimary like concat('%', cast(:q as string), '%')
               )
            """)
    Page<Supplier> search(@Param("q") String q,
                          @Param("activeOnly") boolean activeOnly,
                          Pageable pageable);
}
