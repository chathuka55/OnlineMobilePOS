package com.possaas.sales.web;

import com.possaas.common.api.PageResponse;
import com.possaas.sales.domain.BillStatus;
import com.possaas.sales.dto.SalesDtos.BillResponse;
import com.possaas.sales.dto.SalesDtos.BillSummaryResponse;
import com.possaas.sales.dto.SalesDtos.CheckoutRequest;
import com.possaas.sales.dto.SalesDtos.VoidBillRequest;
import com.possaas.sales.service.BillService;
import com.possaas.sales.service.CheckoutService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bills")
public class BillController {

    private final CheckoutService checkoutService;
    private final BillService billService;

    public BillController(CheckoutService checkoutService, BillService billService) {
        this.checkoutService = checkoutService;
        this.billService = billService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public BillResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return checkoutService.checkout(request);
    }

    @GetMapping("/{billId}")
    public BillResponse get(@PathVariable UUID billId) {
        return billService.get(billId);
    }

    @GetMapping
    public PageResponse<BillSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BillStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50, sort = "billedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return billService.search(q, status, customerId, from, to, pageable);
    }

    @PostMapping("/{billId}/void")
    public BillResponse voidBill(@PathVariable UUID billId,
                                 @RequestBody(required = false) VoidBillRequest request) {
        return billService.voidBill(billId, request == null ? new VoidBillRequest(null) : request);
    }
}
