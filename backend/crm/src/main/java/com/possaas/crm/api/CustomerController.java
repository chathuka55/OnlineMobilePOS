package com.possaas.crm.api;

import com.possaas.common.api.PageResponse;
import com.possaas.crm.api.dto.CustomerDtos.CreditLimitRequest;
import com.possaas.crm.api.dto.CustomerDtos.CustomerNoteRequest;
import com.possaas.crm.api.dto.CustomerDtos.CustomerNoteResponse;
import com.possaas.crm.api.dto.CustomerDtos.CustomerRequest;
import com.possaas.crm.api.dto.CustomerDtos.CustomerResponse;
import com.possaas.crm.api.dto.CustomerDtos.OutstandingAdjustmentRequest;
import com.possaas.crm.service.CustomerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customer.view')")
    public PageResponse<CustomerResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @PageableDefault(size = 50, sort = "displayName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return PageResponse.of(customerService.list(q, activeOnly, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('customer.view')")
    public CustomerResponse get(@PathVariable UUID id) {
        return customerService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer.manage')")
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('customer.manage')")
    public CustomerResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('customer.delete')")
    public void delete(@PathVariable UUID id) {
        customerService.softDelete(id);
    }

    @PutMapping("/{id}/credit-limit")
    @PreAuthorize("hasAuthority('wholesale.credit_limit')")
    public CustomerResponse updateCreditLimit(@PathVariable UUID id,
                                              @Valid @RequestBody CreditLimitRequest request) {
        return customerService.updateCreditLimit(id, request);
    }

    @PostMapping("/{id}/outstanding")
    @PreAuthorize("hasAuthority('customer.manage')")
    public CustomerResponse adjustOutstanding(
            @PathVariable UUID id,
            @Valid @RequestBody OutstandingAdjustmentRequest request) {
        return customerService.adjustOutstanding(id, request);
    }

    @GetMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('customer.view')")
    public List<CustomerNoteResponse> listNotes(@PathVariable UUID id) {
        return customerService.listNotes(id);
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer.manage')")
    public CustomerNoteResponse addNote(@PathVariable UUID id,
                                        @Valid @RequestBody CustomerNoteRequest request) {
        return customerService.addNote(id, request);
    }
}
