package com.flashsale.catalog;

import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        long priceCents
) {

    static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.sku(), product.name(), product.priceCents());
    }
}
