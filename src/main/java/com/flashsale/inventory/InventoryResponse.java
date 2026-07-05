package com.flashsale.inventory;

import java.util.UUID;

public record InventoryResponse(
        UUID productId,
        int available,
        int reserved,
        long version
) {

    static InventoryResponse from(InventoryItem item) {
        return new InventoryResponse(item.productId(), item.available(), item.reserved(), item.version());
    }
}
