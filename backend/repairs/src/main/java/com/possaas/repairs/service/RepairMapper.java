package com.possaas.repairs.service;

import com.possaas.repairs.domain.RepairHistory;
import com.possaas.repairs.domain.RepairLine;
import com.possaas.repairs.domain.RepairLineSerial;
import com.possaas.repairs.domain.RepairOrder;
import com.possaas.repairs.dto.RepairDtos.RepairHistoryResponse;
import com.possaas.repairs.dto.RepairDtos.RepairLineResponse;
import com.possaas.repairs.dto.RepairDtos.RepairLineSerialResponse;
import com.possaas.repairs.dto.RepairDtos.RepairResponse;
import java.util.Arrays;
import java.util.List;

final class RepairMapper {

    private RepairMapper() {
    }

    static RepairResponse toResponse(RepairOrder order) {
        return new RepairResponse(
                order.getId(),
                order.getRepairNumber(),
                order.getStatus(),
                order.getOutletId(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getDeviceType(),
                order.getDeviceBrand(),
                order.getDeviceModel(),
                order.getDeviceSerial(),
                order.getRepairType(),
                order.getReportedFault(),
                order.getDiagnosis(),
                toList(order.getDeviceConditions()),
                toList(order.getBorrowedItems()),
                order.getAccessoriesNote(),
                order.getCurrency(),
                order.getServiceCharge(),
                order.getPartsSubtotal(),
                order.getLineDiscountTotal(),
                order.getDiscountAmount(),
                order.getTaxTotal(),
                order.getGrandTotal(),
                order.getAdvancePaid(),
                order.getAmountPaid(),
                order.getBalanceDue(),
                order.getPartsCost(),
                order.getEstimatedCost(),
                order.getWarrantyDays(),
                order.getWarrantyEndsOn(),
                order.getReceivedAt(),
                order.getPromisedAt(),
                order.getCompletedAt(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                order.getCancelReason(),
                order.getTechnicianId(),
                order.getNote(),
                order.getLines().stream().map(RepairMapper::toLine).toList(),
                order.getHistory().stream().map(RepairMapper::toHistory).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    static RepairLineResponse toLine(RepairLine line) {
        return new RepairLineResponse(
                line.getId(),
                line.getLineNumber(),
                line.getLineType(),
                line.getItemId(),
                line.getItemSku(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitCost(),
                line.getUnitPrice(),
                line.getGrossAmount(),
                line.getDiscountType(),
                line.getDiscountInput(),
                line.getDiscountAmount(),
                line.getNetAmount(),
                line.getTaxRatePercent(),
                line.getTaxAmount(),
                line.getLineTotal(),
                line.getWarrantyLabel(),
                line.getSerials().stream().map(RepairMapper::toSerial).toList()
        );
    }

    static RepairLineSerialResponse toSerial(RepairLineSerial serial) {
        return new RepairLineSerialResponse(
                serial.getId(),
                serial.getItemSerialId(),
                serial.getSerialNumber()
        );
    }

    static RepairHistoryResponse toHistory(RepairHistory history) {
        return new RepairHistoryResponse(
                history.getId(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getNote(),
                history.getChangedAt(),
                history.getChangedBy()
        );
    }

    private static List<String> toList(String[] values) {
        return values == null || values.length == 0 ? List.of() : Arrays.asList(values);
    }
}
