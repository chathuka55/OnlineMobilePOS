package com.possaas.sales.web;

import com.possaas.sales.dto.SalesDtos.ApplyCreditNoteRequest;
import com.possaas.sales.dto.SalesDtos.BillResponse;
import com.possaas.sales.dto.SalesDtos.CreditNoteResponse;
import com.possaas.sales.dto.SalesDtos.IssueCreditNoteRequest;
import com.possaas.sales.service.CreditNoteService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credit-notes")
public class CreditNoteController {

    private final CreditNoteService creditNoteService;

    public CreditNoteController(CreditNoteService creditNoteService) {
        this.creditNoteService = creditNoteService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('credit_note.issue')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditNoteResponse issue(@Valid @RequestBody IssueCreditNoteRequest request) {
        return creditNoteService.issueFromBill(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sale.view')")
    public CreditNoteResponse get(@PathVariable UUID id) {
        return creditNoteService.get(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sale.view')")
    public List<CreditNoteResponse> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "true") boolean openOnly) {
        return creditNoteService.list(customerId, openOnly);
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasAuthority('credit_note.redeem')")
    public BillResponse apply(@PathVariable UUID id,
                              @Valid @RequestBody ApplyCreditNoteRequest request) {
        return creditNoteService.apply(id, request);
    }
}
