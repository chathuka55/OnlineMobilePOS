package com.possaas.catalog.repository;

import com.possaas.catalog.domain.GoodsReceivedNote;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceivedNoteRepository extends JpaRepository<GoodsReceivedNote, UUID> {

    Optional<GoodsReceivedNote> findByGrnNumberIgnoreCase(String grnNumber);

    Page<GoodsReceivedNote> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
