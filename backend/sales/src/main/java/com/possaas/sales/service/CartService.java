package com.possaas.sales.service;

import com.possaas.catalog.domain.Item;
import com.possaas.catalog.repository.ItemRepository;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.common.tenant.TenantContext;
import com.possaas.crm.domain.Customer;
import com.possaas.crm.repository.CustomerRepository;
import com.possaas.sales.domain.Cart;
import com.possaas.sales.domain.CartLine;
import com.possaas.sales.domain.CartStatus;
import com.possaas.sales.domain.DiscountType;
import com.possaas.sales.domain.PriceMode;
import com.possaas.sales.dto.SalesDtos.CartLineRequest;
import com.possaas.sales.dto.SalesDtos.CartResponse;
import com.possaas.sales.dto.SalesDtos.CreateCartRequest;
import com.possaas.sales.dto.SalesDtos.HoldCartRequest;
import com.possaas.sales.dto.SalesDtos.UpdateCartRequest;
import com.possaas.sales.repository.CartRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ItemRepository itemRepository;
    private final CustomerRepository customerRepository;

    public CartService(CartRepository cartRepository,
                       ItemRepository itemRepository,
                       CustomerRepository customerRepository) {
        this.cartRepository = cartRepository;
        this.itemRepository = itemRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CartResponse create(CreateCartRequest request) {
        Cart cart = new Cart();
        cart.setOutletId(TenantContext.requireOutletId());
        cart.setStatus(CartStatus.DRAFT);
        cart.setPriceMode(request.priceMode() == null ? PriceMode.RETAIL : request.priceMode());
        cart.setLabel(request.label());
        cart.setNote(request.note());
        cart.setCreatedBy(TenantContext.userIdOrNull());
        applyCustomer(cart, request.customerId(), request.customerName());
        return SalesMapper.toCart(cartRepository.save(cart));
    }

    @Transactional(readOnly = true)
    public CartResponse get(UUID cartId) {
        return SalesMapper.toCart(requireEditableOrAny(cartId));
    }

    @Transactional(readOnly = true)
    public List<CartResponse> listActive() {
        UUID outletId = TenantContext.requireOutletId();
        return cartRepository.findByOutletIdAndStatusInWithLines(
                        outletId, List.of(CartStatus.DRAFT, CartStatus.HELD))
                .stream()
                .sorted(Comparator.comparing(Cart::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(SalesMapper::toCart)
                .toList();
    }

    @Transactional
    public CartResponse update(UUID cartId, UpdateCartRequest request) {
        Cart cart = requireEditable(cartId);
        if (request.priceMode() != null) {
            cart.setPriceMode(request.priceMode());
        }
        if (request.label() != null) {
            cart.setLabel(request.label());
        }
        if (request.note() != null) {
            cart.setNote(request.note());
        }
        if (request.customerId() != null || request.customerName() != null) {
            applyCustomer(cart, request.customerId(), request.customerName());
        }
        return SalesMapper.toCart(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse addLine(UUID cartId, CartLineRequest request) {
        Cart cart = requireEditable(cartId);
        if (cart.getStatus() == CartStatus.HELD) {
            cart.setStatus(CartStatus.DRAFT);
            cart.setHeldAt(null);
        }
        Item item = requireSellableItem(request.itemId());
        CartLine line = new CartLine();
        line.setItemId(item.getId());
        line.setLineNumber(nextLineNumber(cart));
        line.setDescription(request.description() != null ? request.description() : item.getName());
        line.setQuantity(Money.quantity(request.quantity()));
        line.setUnitPrice(resolveUnitPrice(item, cart.getPriceMode(), request.unitPrice()));
        line.setDiscountType(request.discountType() == null ? DiscountType.NONE : request.discountType());
        line.setDiscountInput(Money.of(request.discountInput()));
        line.setTaxRateId(request.taxRateId() != null ? request.taxRateId() : item.getTaxRateId());
        line.setWarrantyLabel(request.warrantyLabel() != null ? request.warrantyLabel() : item.getWarrantyLabel());
        line.setSerialIds(toArray(request.serialIds()));
        validateSerialCount(item, line.getQuantity(), line.getSerialIds());
        cart.addLine(line);
        return SalesMapper.toCart(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateLine(UUID cartId, UUID lineId, CartLineRequest request) {
        Cart cart = requireEditable(cartId);
        CartLine line = cart.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("CartLine", lineId));
        Item item = requireSellableItem(request.itemId() != null ? request.itemId() : line.getItemId());
        line.setItemId(item.getId());
        if (request.description() != null) {
            line.setDescription(request.description());
        }
        line.setQuantity(Money.quantity(request.quantity()));
        line.setUnitPrice(resolveUnitPrice(item, cart.getPriceMode(), request.unitPrice()));
        if (request.discountType() != null) {
            line.setDiscountType(request.discountType());
        }
        if (request.discountInput() != null) {
            line.setDiscountInput(Money.of(request.discountInput()));
        }
        if (request.taxRateId() != null) {
            line.setTaxRateId(request.taxRateId());
        }
        if (request.warrantyLabel() != null) {
            line.setWarrantyLabel(request.warrantyLabel());
        }
        if (request.serialIds() != null) {
            line.setSerialIds(toArray(request.serialIds()));
        }
        validateSerialCount(item, line.getQuantity(), line.getSerialIds());
        return SalesMapper.toCart(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeLine(UUID cartId, UUID lineId) {
        Cart cart = requireEditable(cartId);
        CartLine line = cart.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("CartLine", lineId));
        cart.removeLine(line);
        renumber(cart);
        return SalesMapper.toCart(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse hold(UUID cartId, HoldCartRequest request) {
        Cart cart = requireEditable(cartId);
        if (cart.getLines().isEmpty()) {
            throw ApiException.of(ErrorCode.CART_EMPTY, "Cannot hold an empty cart");
        }
        cart.setStatus(CartStatus.HELD);
        cart.setHeldAt(Instant.now());
        if (request != null && request.label() != null) {
            cart.setLabel(request.label());
        }
        return SalesMapper.toCart(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse resume(UUID cartId) {
        Cart cart = cartRepository.findByIdWithLines(cartId)
                .orElseThrow(() -> ApiException.notFound("Cart", cartId));
        if (cart.getStatus() != CartStatus.HELD) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only held carts can be resumed");
        }
        cart.setStatus(CartStatus.DRAFT);
        cart.setHeldAt(null);
        return SalesMapper.toCart(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse abandon(UUID cartId) {
        Cart cart = requireEditable(cartId);
        cart.setStatus(CartStatus.ABANDONED);
        return SalesMapper.toCart(cartRepository.save(cart));
    }

    Cart requireEditable(UUID cartId) {
        Cart cart = cartRepository.findByIdWithLines(cartId)
                .orElseThrow(() -> ApiException.notFound("Cart", cartId));
        if (cart.getStatus() == CartStatus.CONVERTED) {
            throw ApiException.of(ErrorCode.CART_ALREADY_CONVERTED, "Cart already converted to a bill");
        }
        if (!cart.isEditable()) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cart is not editable in status " + cart.getStatus());
        }
        return cart;
    }

    private Cart requireEditableOrAny(UUID cartId) {
        return cartRepository.findByIdWithLines(cartId)
                .orElseThrow(() -> ApiException.notFound("Cart", cartId));
    }

    private void applyCustomer(Cart cart, UUID customerId, String customerName) {
        if (customerId != null) {
            Customer customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
                    .orElseThrow(() -> ApiException.notFound("Customer", customerId));
            cart.setCustomerId(customer.getId());
            cart.setCustomerName(customerName != null ? customerName : customer.getDisplayName());
        } else if (customerName != null) {
            cart.setCustomerId(null);
            cart.setCustomerName(customerName);
        }
    }

    private Item requireSellableItem(UUID itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("Item", itemId));
        if (!item.isSellable()) {
            throw ApiException.of(ErrorCode.ITEM_NOT_SELLABLE, "Item is not sellable")
                    .with("itemId", itemId);
        }
        return item;
    }

    private static BigDecimal resolveUnitPrice(Item item, PriceMode mode, BigDecimal override) {
        if (override != null) {
            return Money.of(override);
        }
        return Money.of(item.priceFor(mode == PriceMode.WHOLESALE));
    }

    private static short nextLineNumber(Cart cart) {
        return (short) (cart.getLines().stream().mapToInt(CartLine::getLineNumber).max().orElse(0) + 1);
    }

    private static void renumber(Cart cart) {
        short n = 1;
        for (CartLine line : cart.getLines()) {
            line.setLineNumber(n++);
        }
    }

    private static UUID[] toArray(List<UUID> serialIds) {
        if (serialIds == null || serialIds.isEmpty()) {
            return new UUID[0];
        }
        return serialIds.toArray(UUID[]::new);
    }

    private static void validateSerialCount(Item item, BigDecimal quantity, UUID[] serialIds) {
        if (!item.isHasSerialTracking()) {
            return;
        }
        int count = serialIds == null ? 0 : serialIds.length;
        if (quantity.stripTrailingZeros().scale() > 0
                || quantity.intValueExact() != count) {
            throw ApiException.of(ErrorCode.SERIAL_COUNT_MISMATCH,
                            "Serialised items require one serial per unit")
                    .with("quantity", quantity)
                    .with("serialCount", count);
        }
    }
}
