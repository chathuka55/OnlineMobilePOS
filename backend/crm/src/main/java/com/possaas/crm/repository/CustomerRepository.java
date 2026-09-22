package com.possaas.crm.repository;

import com.possaas.crm.domain.Customer;
import com.possaas.crm.domain.CustomerType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Customer> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    @Query("""
            select c from Customer c
             where c.deletedAt is null
               and (:activeOnly = false or c.active = true)
               and c.customerType in :types
               and (
                    :q is null or :q = ''
                    or lower(c.displayName) like lower(concat('%', cast(:q as string), '%'))
                    or lower(coalesce(c.code, '')) like lower(concat('%', cast(:q as string), '%'))
                    or lower(coalesce(c.phonePrimary, '')) like lower(concat('%', cast(:q as string), '%'))
               )
            """)
    Page<Customer> search(@Param("q") String q,
                          @Param("activeOnly") boolean activeOnly,
                          @Param("types") List<CustomerType> types,
                          Pageable pageable);
}
