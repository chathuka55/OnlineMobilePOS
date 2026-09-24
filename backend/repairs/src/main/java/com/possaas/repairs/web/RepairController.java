package com.possaas.repairs.web;

import com.possaas.common.api.PageResponse;
import com.possaas.repairs.domain.RepairOrderStatus;
import com.possaas.repairs.dto.RepairDtos.ApproveRepairRequest;
import com.possaas.repairs.dto.RepairDtos.CreateRepairRequest;
import com.possaas.repairs.dto.RepairDtos.PaymentRequest;
import com.possaas.repairs.dto.RepairDtos.RefundRequest;
import com.possaas.repairs.dto.RepairDtos.RepairLineRequest;
import com.possaas.repairs.dto.RepairDtos.RepairResponse;
import com.possaas.repairs.dto.RepairDtos.TransitionRequest;
import com.possaas.repairs.dto.RepairDtos.UpdateRepairRequest;
import com.possaas.repairs.service.RepairService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/repairs")
public class RepairController {

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @GetMapping
    public PageResponse<RepairResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RepairOrderStatus status,
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 50, sort = "receivedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return repairService.list(q, status, customerId, pageable);
    }

    @GetMapping("/{id}")
    public RepairResponse get(@PathVariable UUID id) {
        return repairService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepairResponse create(@Valid @RequestBody CreateRepairRequest request) {
        return repairService.create(request);
    }

    @PutMapping("/{id}")
    public RepairResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody UpdateRepairRequest request) {
        return repairService.update(id, request);
    }

    @PostMapping("/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public RepairResponse addLine(@PathVariable UUID id,
                                  @Valid @RequestBody RepairLineRequest request) {
        return repairService.addLine(id, request);
    }

    @PostMapping("/{id}/transition")
    public RepairResponse transition(@PathVariable UUID id,
                                     @Valid @RequestBody TransitionRequest request) {
        return repairService.transition(id, request);
    }

    @PostMapping("/{id}/consume-parts")
    public RepairResponse consumeParts(@PathVariable UUID id) {
        return repairService.consumeParts(id);
    }

    @PostMapping("/{id}/approve")
    public RepairResponse approve(@PathVariable UUID id,
                                  @Valid @RequestBody ApproveRepairRequest request) {
        return repairService.approve(id, request);
    }

    @PostMapping("/{id}/payments")
    public RepairResponse collectPayment(@PathVariable UUID id,
                                         @Valid @RequestBody PaymentRequest request) {
        return repairService.collectPayment(id, request);
    }

    @PostMapping("/{id}/refund")
    public RepairResponse refund(@PathVariable UUID id,
                                 @Valid @RequestBody RefundRequest request) {
        return repairService.refund(id, request);
    }
}
