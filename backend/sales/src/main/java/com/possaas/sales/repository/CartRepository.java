package com.possaas.sales.repository;

import com.possaas.sales.domain.Cart;
import com.possaas.sales.domain.CartStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.lines WHERE c.id = :id")
    Optional<Cart> findByIdWithLines(@Param("id") UUID id);

    @Query("""
            SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.lines
             WHERE c.outletId = :outletId AND c.status IN :statuses
            """)
    List<Cart> findByOutletIdAndStatusInWithLines(
            @Param("outletId") UUID outletId, @Param("statuses") List<CartStatus> statuses);
}
