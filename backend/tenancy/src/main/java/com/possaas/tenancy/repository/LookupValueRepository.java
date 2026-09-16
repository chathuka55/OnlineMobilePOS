package com.possaas.tenancy.repository;

import com.possaas.tenancy.domain.LookupValue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LookupValueRepository extends JpaRepository<LookupValue, UUID> {

    List<LookupValue> findByLookupTypeAndActiveIsTrueOrderByDisplayOrderAscValueAsc(
            LookupValue.LookupType lookupType);

    boolean existsByLookupTypeAndValueIgnoreCase(LookupValue.LookupType lookupType, String value);
}
