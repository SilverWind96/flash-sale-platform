package com.flashsale.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
class CheckoutController {

    private final CheckoutService checkoutService;

    CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CheckoutResponse checkout(@RequestHeader("Idempotency-Key") String idempotencyKey,
                              @Valid @RequestBody CheckoutRequest request) {
        return checkoutService.checkout(new CheckoutCommand(
                request.productId(),
                request.quantity(),
                idempotencyKey
        ));
    }
}
