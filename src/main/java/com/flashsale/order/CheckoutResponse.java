package com.flashsale.order;

import java.util.UUID;

public record CheckoutResponse(
        UUID orderId,
        UUID productId,
        int quantity,
        long amountCents,
        OrderStatus status,
        String idempotencyKey
) {

    static CheckoutResponse from(CustomerOrder order) {
        return new CheckoutResponse(
                order.id(),
                order.productId(),
                order.quantity(),
                order.amountCents(),
                order.status(),
                order.idempotencyKey()
        );
    }
}
