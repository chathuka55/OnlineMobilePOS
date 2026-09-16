package com.possaas.sales.web;

import com.possaas.sales.dto.SalesDtos.CartLineRequest;
import com.possaas.sales.dto.SalesDtos.CartResponse;
import com.possaas.sales.dto.SalesDtos.CreateCartRequest;
import com.possaas.sales.dto.SalesDtos.HoldCartRequest;
import com.possaas.sales.dto.SalesDtos.UpdateCartRequest;
import com.possaas.sales.service.CartService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse create(@Valid @RequestBody CreateCartRequest request) {
        return cartService.create(request);
    }

    @GetMapping
    public List<CartResponse> listActive() {
        return cartService.listActive();
    }

    @GetMapping("/{cartId}")
    public CartResponse get(@PathVariable UUID cartId) {
        return cartService.get(cartId);
    }

    @PutMapping("/{cartId}")
    public CartResponse update(@PathVariable UUID cartId,
                               @Valid @RequestBody UpdateCartRequest request) {
        return cartService.update(cartId, request);
    }

    @PostMapping("/{cartId}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addLine(@PathVariable UUID cartId,
                                @Valid @RequestBody CartLineRequest request) {
        return cartService.addLine(cartId, request);
    }

    @PutMapping("/{cartId}/lines/{lineId}")
    public CartResponse updateLine(@PathVariable UUID cartId,
                                   @PathVariable UUID lineId,
                                   @Valid @RequestBody CartLineRequest request) {
        return cartService.updateLine(cartId, lineId, request);
    }

    @DeleteMapping("/{cartId}/lines/{lineId}")
    public CartResponse removeLine(@PathVariable UUID cartId, @PathVariable UUID lineId) {
        return cartService.removeLine(cartId, lineId);
    }

    @PostMapping("/{cartId}/hold")
    public CartResponse hold(@PathVariable UUID cartId,
                             @RequestBody(required = false) HoldCartRequest request) {
        return cartService.hold(cartId, request == null ? new HoldCartRequest(null) : request);
    }

    @PostMapping("/{cartId}/resume")
    public CartResponse resume(@PathVariable UUID cartId) {
        return cartService.resume(cartId);
    }

    @PostMapping("/{cartId}/abandon")
    public CartResponse abandon(@PathVariable UUID cartId) {
        return cartService.abandon(cartId);
    }
}
