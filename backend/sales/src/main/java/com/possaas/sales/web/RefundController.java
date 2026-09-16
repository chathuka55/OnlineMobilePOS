package com.possaas.sales.web;

import com.possaas.sales.dto.SalesDtos.CreateRefundRequest;
import com.possaas.sales.dto.SalesDtos.RefundResponse;
import com.possaas.sales.service.RefundService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse create(@Valid @RequestBody CreateRefundRequest request) {
        return refundService.create(request);
    }

    @GetMapping("/{id}")
    public RefundResponse get(@PathVariable UUID id) {
        return refundService.get(id);
    }
}
