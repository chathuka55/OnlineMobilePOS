package com.possaas.wholesale.web;

import com.possaas.common.api.PageResponse;
import com.possaas.wholesale.domain.WholesaleInvoiceStatus;
import com.possaas.wholesale.dto.WholesaleDtos.ClearChequeRequest;
import com.possaas.wholesale.dto.WholesaleDtos.CollectionRequest;
import com.possaas.wholesale.dto.WholesaleDtos.CreateInvoiceRequest;
import com.possaas.wholesale.dto.WholesaleDtos.CreditLedgerResponse;
import com.possaas.wholesale.dto.WholesaleDtos.InvoiceLineRequest;
import com.possaas.wholesale.dto.WholesaleDtos.InvoiceResponse;
import com.possaas.wholesale.dto.WholesaleDtos.PaymentResponse;
import com.possaas.wholesale.dto.WholesaleDtos.UpdateInvoiceRequest;
import com.possaas.wholesale.dto.WholesaleDtos.VoidRequest;
import com.possaas.wholesale.service.WholesaleInvoiceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/wholesale")
public class WholesaleInvoiceController {

    private final WholesaleInvoiceService invoiceService;

    public WholesaleInvoiceController(WholesaleInvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/invoices")
    public PageResponse<InvoiceResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) WholesaleInvoiceStatus status,
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 50, sort = "issuedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return invoiceService.list(q, status, customerId, pageable);
    }

    @GetMapping("/invoices/{id}")
    public InvoiceResponse get(@PathVariable UUID id) {
        return invoiceService.get(id);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request) {
        return invoiceService.create(request);
    }

    @PutMapping("/invoices/{id}")
    public InvoiceResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateInvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    @PostMapping("/invoices/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse addLine(@PathVariable UUID id,
                                   @Valid @RequestBody InvoiceLineRequest request) {
        return invoiceService.addLine(id, request);
    }

    @DeleteMapping("/invoices/{id}/lines/{lineId}")
    public InvoiceResponse removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        return invoiceService.removeLine(id, lineId);
    }

    @PostMapping("/invoices/{id}/post")
    public InvoiceResponse post(@PathVariable UUID id) {
        return invoiceService.post(id);
    }

    @PostMapping("/invoices/{id}/void")
    public InvoiceResponse voidInvoice(@PathVariable UUID id,
                                       @RequestBody(required = false) VoidRequest request) {
        return invoiceService.voidInvoice(id, request == null ? new VoidRequest(null) : request);
    }

    @GetMapping("/invoices/{id}/payments")
    public List<PaymentResponse> listPayments(@PathVariable UUID id) {
        return invoiceService.listPayments(id);
    }

    @PostMapping("/invoices/{id}/collections")
    public InvoiceResponse collect(@PathVariable UUID id,
                                   @Valid @RequestBody CollectionRequest request) {
        return invoiceService.collect(id, request);
    }

    @PostMapping("/invoices/{invoiceId}/payments/{paymentId}/cheque-status")
    public PaymentResponse clearCheque(@PathVariable UUID invoiceId,
                                       @PathVariable UUID paymentId,
                                       @Valid @RequestBody ClearChequeRequest request) {
        return invoiceService.clearCheque(invoiceId, paymentId, request);
    }

    @GetMapping("/collections")
    public PageResponse<InvoiceResponse> collections(
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 50, sort = "dueDate", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return invoiceService.collections(customerId, pageable);
    }

    @GetMapping("/customers/{customerId}/credit-ledger")
    public PageResponse<CreditLedgerResponse> creditLedger(
            @PathVariable UUID customerId,
            @PageableDefault(size = 50) Pageable pageable) {
        return invoiceService.customerLedger(customerId, pageable);
    }
}
