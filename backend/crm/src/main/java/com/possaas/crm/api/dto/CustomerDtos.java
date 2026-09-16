package com.possaas.crm.api.dto;

import com.possaas.crm.domain.Customer;
import com.possaas.crm.domain.CustomerNote;
import com.possaas.crm.domain.CustomerType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerRequest(
            @Size(max = 30) String code,
            @NotBlank @Size(max = 160) String displayName,
            @NotNull CustomerType customerType,
            @Size(max = 32) String phonePrimary,
            @Size(max = 32) String phoneSecondary,
            @Size(max = 255) String email,
            @Size(max = 180) String addressLine1,
            @Size(max = 180) String addressLine2,
            @Size(max = 90) String city,
            @Size(max = 60) String taxIdentifier,
            @DecimalMin("0") BigDecimal creditLimit,
            UUID defaultTaxRateId,
            String notes,
            Boolean active
    ) {
    }

    public record CustomerResponse(
            UUID id,
            String code,
            String displayName,
            CustomerType customerType,
            String phonePrimary,
            String phoneSecondary,
            String email,
            String addressLine1,
            String addressLine2,
            String city,
            String taxIdentifier,
            BigDecimal creditLimit,
            BigDecimal outstandingAmount,
            BigDecimal lifetimeSales,
            int loyaltyPoints,
            UUID defaultTaxRateId,
            String notes,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static CustomerResponse from(Customer customer) {
            return new CustomerResponse(
                    customer.getId(),
                    customer.getCode(),
                    customer.getDisplayName(),
                    customer.getCustomerType(),
                    customer.getPhonePrimary(),
                    customer.getPhoneSecondary(),
                    customer.getEmail(),
                    customer.getAddressLine1(),
                    customer.getAddressLine2(),
                    customer.getCity(),
                    customer.getTaxIdentifier(),
                    customer.getCreditLimit(),
                    customer.getOutstandingAmount(),
                    customer.getLifetimeSales(),
                    customer.getLoyaltyPoints(),
                    customer.getDefaultTaxRateId(),
                    customer.getNotes(),
                    customer.isActive(),
                    customer.getCreatedAt(),
                    customer.getUpdatedAt());
        }
    }

    public record CreditLimitRequest(
            @NotNull @DecimalMin("0") BigDecimal creditLimit
    ) {
    }

    public record OutstandingAdjustmentRequest(
            @NotNull BigDecimal delta,
            String reason
    ) {
    }

    public record CustomerNoteRequest(
            @NotBlank String body
    ) {
    }

    public record CustomerNoteResponse(
            UUID id,
            UUID customerId,
            String body,
            Instant createdAt,
            UUID createdBy
    ) {
        public static CustomerNoteResponse from(CustomerNote note) {
            return new CustomerNoteResponse(
                    note.getId(),
                    note.getCustomerId(),
                    note.getBody(),
                    note.getCreatedAt(),
                    note.getCreatedBy());
        }
    }
}
