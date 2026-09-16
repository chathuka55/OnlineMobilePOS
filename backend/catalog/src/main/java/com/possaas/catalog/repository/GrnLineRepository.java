package com.possaas.catalog.repository;

import com.possaas.catalog.domain.GrnLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrnLineRepository extends JpaRepository<GrnLine, UUID> {

    List<GrnLine> findByGrnIdOrderByLineNumberAsc(UUID grnId);
}
