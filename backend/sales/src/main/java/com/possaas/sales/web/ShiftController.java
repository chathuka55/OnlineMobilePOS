package com.possaas.sales.web;

import com.possaas.common.api.PageResponse;
import com.possaas.sales.domain.ShiftStatus;
import com.possaas.sales.dto.ShiftDtos.CashMovementRequest;
import com.possaas.sales.dto.ShiftDtos.CloseShiftRequest;
import com.possaas.sales.dto.ShiftDtos.OpenShiftRequest;
import com.possaas.sales.dto.ShiftDtos.ShiftReport;
import com.possaas.sales.service.ShiftService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/shifts")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('report.view')")
    public PageResponse<ShiftReport> list(
            @RequestParam(required = false) ShiftStatus status,
            @PageableDefault(size = 50, sort = "openedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return shiftService.list(status, pageable);
    }

    /** The X-report for the till the caller is signed in to. */
    @GetMapping("/current")
    @PreAuthorize("hasAuthority('sale.create')")
    public ShiftReport current(@RequestParam(required = false) UUID outletId) {
        return shiftService.current(outletId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('report.view')")
    public ShiftReport get(@PathVariable UUID id) {
        return shiftService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sale.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftReport open(@Valid @RequestBody(required = false) OpenShiftRequest request) {
        return shiftService.open(request == null
                ? new OpenShiftRequest(null, null, null)
                : request);
    }

    @PostMapping("/{id}/cash-movements")
    @PreAuthorize("hasAuthority('payment.record')")
    public ShiftReport recordMovement(@PathVariable UUID id,
                                      @Valid @RequestBody CashMovementRequest request) {
        return shiftService.recordMovement(id, request);
    }

    /** The Z-report: count the drawer, post the variance, freeze the shift. */
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('report.financial')")
    public ShiftReport close(@PathVariable UUID id,
                             @Valid @RequestBody CloseShiftRequest request) {
        return shiftService.close(id, request);
    }
}
