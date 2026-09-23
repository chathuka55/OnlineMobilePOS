package com.possaas.catalog.repository;

import com.possaas.catalog.domain.ItemSerial;
import com.possaas.catalog.domain.SerialStatus;
import com.possaas.catalog.domain.SoldDocumentType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemSerialRepository extends JpaRepository<ItemSerial, UUID> {

    Optional<ItemSerial> findBySerialNumberIgnoreCase(String serialNumber);

    boolean existsBySerialNumberIgnoreCase(String serialNumber);

    List<ItemSerial> findByIdIn(Collection<UUID> ids);

    List<ItemSerial> findBySoldDocumentTypeAndSoldDocumentId(SoldDocumentType type, UUID documentId);

    List<ItemSerial> findByItemIdAndStatus(UUID itemId, SerialStatus status);

    List<ItemSerial> findByGrnIdOrderBySerialNumberAsc(UUID grnId);

    @Query("""
            select s from ItemSerial s
             where (:q is null or :q = ''
                    or lower(s.serialNumber) like lower(concat('%', cast(:q as string), '%'))
                    or s.imei1 like concat('%', cast(:q as string), '%')
                    or s.imei2 like concat('%', cast(:q as string), '%'))
               and (:status is null or s.status = :status)
               and (:itemId is null or s.itemId = :itemId)
            """)
    Page<ItemSerial> search(@Param("q") String q,
                            @Param("status") SerialStatus status,
                            @Param("itemId") UUID itemId,
                            Pageable pageable);

    /** An IMEI belongs to one handset, so it must not already sit in either column. */
    @Query("select (count(s) > 0) from ItemSerial s where s.imei1 = :imei or s.imei2 = :imei")
    boolean existsByAnyImei(@Param("imei") String imei);

    /** Counter lookup: the cashier scans the handset, not our internal serial. */
    @Query("""
            select s from ItemSerial s
             where s.imei1 = :code or s.imei2 = :code
                or lower(s.serialNumber) = lower(cast(:code as string))
            """)
    Optional<ItemSerial> findByImeiOrSerial(@Param("code") String code);
}
