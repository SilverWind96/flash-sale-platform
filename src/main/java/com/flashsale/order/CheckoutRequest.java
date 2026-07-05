package com.flashsale.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckoutRequest(
        @NotNull UUID productId,
        @Min(1) int quantity
) {
}
