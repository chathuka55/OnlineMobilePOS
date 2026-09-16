package com.possaas.catalog.repository;

import com.possaas.catalog.domain.ItemBarcode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemBarcodeRepository extends JpaRepository<ItemBarcode, UUID> {

    Optional<ItemBarcode> findByBarcode(String barcode);

    List<ItemBarcode> findByItemIdOrderByPrimaryBarcodeDescCreatedAtAsc(UUID itemId);

    void deleteByItemId(UUID itemId);

    boolean existsByBarcodeAndItemIdNot(String barcode, UUID itemId);

    boolean existsByBarcode(String barcode);
}
