package com.flashsale.inventory;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/products/{productId}/inventory")
class InventoryController {

    private final InventoryService inventoryService;

    InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/stock")
    InventoryResponse addStock(@PathVariable UUID productId, @Valid @RequestBody AddStockRequest request) {
        return inventoryService.addStock(productId, request);
    }

    @GetMapping
    InventoryResponse getInventory(@PathVariable UUID productId) {
        return inventoryService.getInventory(productId);
    }
}
