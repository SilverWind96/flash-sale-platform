package com.flashsale.order;

import java.util.UUID;

public record CheckoutCommand(
        UUID productId,
        int quantity,
        String idempotencyKey
) {
}
