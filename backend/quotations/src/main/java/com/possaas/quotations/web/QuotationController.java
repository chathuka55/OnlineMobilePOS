package com.possaas.quotations.web;

import com.possaas.common.api.PageResponse;
import com.possaas.quotations.domain.QuotationStatus;
import com.possaas.quotations.dto.QuotationDtos.ConvertResponse;
import com.possaas.quotations.dto.QuotationDtos.CreateQuotationRequest;
import com.possaas.quotations.dto.QuotationDtos.QuotationLineRequest;
import com.possaas.quotations.dto.QuotationDtos.QuotationPrintResponse;
import com.possaas.quotations.dto.QuotationDtos.QuotationResponse;
import com.possaas.quotations.dto.QuotationDtos.TransitionRequest;
import com.possaas.quotations.dto.QuotationDtos.UpdateQuotationRequest;
import com.possaas.quotations.service.QuotationService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/quotations")
public class QuotationController {

    private final QuotationService quotationService;

    public QuotationController(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('quotation.view')")
    public PageResponse<QuotationResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) QuotationStatus status,
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 50, sort = "quotedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return quotationService.list(q, status, customerId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('quotation.view')")
    public QuotationResponse get(@PathVariable UUID id) {
        return quotationService.get(id);
    }

    @GetMapping("/{id}/print")
    @PreAuthorize("hasAuthority('quotation.view')")
    public QuotationPrintResponse print(@PathVariable UUID id) {
        return quotationService.print(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('quotation.manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public QuotationResponse create(@Valid @RequestBody CreateQuotationRequest request) {
        return quotationService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('quotation.manage')")
    public QuotationResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody UpdateQuotationRequest request) {
        return quotationService.update(id, request);
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('quotation.manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public QuotationResponse addLine(@PathVariable UUID id,
                                     @Valid @RequestBody QuotationLineRequest request) {
        return quotationService.addLine(id, request);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('quotation.manage')")
    public QuotationResponse removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        return quotationService.removeLine(id, lineId);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAuthority('quotation.convert')")
    public QuotationResponse transition(@PathVariable UUID id,
                                        @Valid @RequestBody TransitionRequest request) {
        return quotationService.transition(id, request);
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAuthority('quotation.convert')")
    public ConvertResponse convert(@PathVariable UUID id) {
        return quotationService.convertToCart(id);
    }
}
