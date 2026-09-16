package com.possaas.crm.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.crm.api.dto.CustomerDtos.CreditLimitRequest;
import com.possaas.crm.api.dto.CustomerDtos.CustomerNoteRequest;
import com.possaas.crm.api.dto.CustomerDtos.CustomerNoteResponse;
import com.possaas.crm.api.dto.CustomerDtos.CustomerRequest;
import com.possaas.crm.api.dto.CustomerDtos.CustomerResponse;
import com.possaas.crm.api.dto.CustomerDtos.OutstandingAdjustmentRequest;
import com.possaas.crm.domain.Customer;
import com.possaas.crm.domain.CustomerNote;
import com.possaas.crm.domain.CustomerType;
import com.possaas.crm.repository.CustomerNoteRepository;
import com.possaas.crm.repository.CustomerRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerNoteRepository customerNoteRepository;

    public CustomerService(CustomerRepository customerRepository,
                           CustomerNoteRepository customerNoteRepository) {
        this.customerRepository = customerRepository;
        this.customerNoteRepository = customerNoteRepository;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(String q, boolean activeOnly, Pageable pageable) {
        return customerRepository.search(blankToNull(q), activeOnly, pageable)
                .map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        return CustomerResponse.from(requireActive(id));
    }

    @Transactional(readOnly = true)
    public Customer require(UUID id) {
        return requireActive(id);
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        assertUniqueCode(request.code(), null);
        Customer customer = new Customer();
        apply(customer, request);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = requireActive(id);
        assertUniqueCode(request.code(), id);
        apply(customer, request);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public void softDelete(UUID id) {
        Customer customer = requireActive(id);
        customer.softDelete();
        customerRepository.save(customer);
    }

    @Transactional
    public CustomerResponse updateCreditLimit(UUID id, CreditLimitRequest request) {
        Customer customer = requireActive(id);
        try {
            customer.updateCreditLimit(request.creditLimit());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation(ex.getMessage());
        }
        return CustomerResponse.from(customerRepository.save(customer));
    }

    /**
     * Adjusts the denormalised outstanding balance. Positive delta increases what the
     * customer owes; called from sales/wholesale in the same transaction as the document.
     */
    @Transactional
    public CustomerResponse adjustOutstanding(UUID id, OutstandingAdjustmentRequest request) {
        Customer customer = requireActive(id);
        customer.adjustOutstanding(Money.of(request.delta()));
        if (customer.getOutstandingAmount().signum() < 0) {
            throw ApiException.validation("Outstanding amount cannot go below zero")
                    .with("outstandingAmount", customer.getOutstandingAmount());
        }
        // credit_limit 0 means no credit allowed (see V5). Any positive outstanding then exceeds.
        if (Money.isPositive(customer.getOutstandingAmount())
                && Money.gt(customer.getOutstandingAmount(), customer.getCreditLimit())) {
            throw ApiException.of(ErrorCode.CREDIT_LIMIT_EXCEEDED,
                            "Customer would exceed their credit limit")
                    .with("creditLimit", customer.getCreditLimit())
                    .with("outstandingAmount", customer.getOutstandingAmount());
        }
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public CustomerNoteResponse addNote(UUID customerId, CustomerNoteRequest request) {
        requireActive(customerId);
        CustomerNote note = customerNoteRepository.save(
                CustomerNote.of(customerId, request.body().trim()));
        return CustomerNoteResponse.from(note);
    }

    @Transactional(readOnly = true)
    public List<CustomerNoteResponse> listNotes(UUID customerId) {
        requireActive(customerId);
        return customerNoteRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(CustomerNoteResponse::from)
                .toList();
    }

    private void apply(Customer customer, CustomerRequest request) {
        customer.setCode(blankToNull(request.code()));
        customer.setDisplayName(request.displayName().trim());
        customer.setCustomerType(request.customerType() == null
                ? CustomerType.RETAIL : request.customerType());
        customer.setPhonePrimary(blankToNull(request.phonePrimary()));
        customer.setPhoneSecondary(blankToNull(request.phoneSecondary()));
        customer.setEmail(blankToNull(request.email()) == null
                ? null : request.email().trim().toLowerCase());
        customer.setAddressLine1(blankToNull(request.addressLine1()));
        customer.setAddressLine2(blankToNull(request.addressLine2()));
        customer.setCity(blankToNull(request.city()));
        customer.setTaxIdentifier(blankToNull(request.taxIdentifier()));
        if (request.creditLimit() != null) {
            customer.updateCreditLimit(request.creditLimit());
        }
        customer.setDefaultTaxRateId(request.defaultTaxRateId());
        customer.setNotes(blankToNull(request.notes()));
        if (request.active() != null) {
            customer.setActive(request.active());
        }
    }

    private Customer requireActive(UUID id) {
        return customerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Customer", id));
    }

    private void assertUniqueCode(String code, UUID excludingId) {
        if (code == null || code.isBlank()) {
            return;
        }
        customerRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(code.trim())
                .filter(existing -> excludingId == null || !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("Customer code already in use")
                            .with("code", code.trim());
                });
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
