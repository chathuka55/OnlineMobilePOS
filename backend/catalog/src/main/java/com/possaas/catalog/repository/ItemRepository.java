package com.possaas.catalog.repository;

import com.possaas.catalog.domain.Item;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    Optional<Item> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Item> findBySkuIgnoreCaseAndDeletedAtIsNull(String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id and i.deletedAt is null")
    Optional<Item> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select i from Item i
             where i.deletedAt is null
               and (:activeOnly = false or i.active = true)
               and (
                    :q is null or :q = ''
                    or lower(i.sku) like lower(concat('%', cast(:q as string), '%'))
                    or lower(i.name) like lower(concat('%', cast(:q as string), '%'))
                    or exists (
                        select 1 from ItemBarcode b
                         where b.itemId = i.id
                           and lower(b.barcode) like lower(concat('%', cast(:q as string), '%'))
                    )
               )
            """)
    Page<Item> search(@Param("q") String q,
                      @Param("activeOnly") boolean activeOnly,
                      Pageable pageable);

    @Query("""
            select i from Item i
             where i.deletedAt is null
               and i.active = true
               and i.trackInventory = true
               and i.quantityOnHand <= i.reorderLevel
            order by i.name asc
            """)
    Page<Item> findLowStock(Pageable pageable);
}
