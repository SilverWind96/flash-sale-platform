package com.flashsale.inventory;

import com.flashsale.catalog.CatalogService;
import com.flashsale.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InventoryService {

    private final CatalogService catalogService;
    private final InventoryRepository inventoryRepository;

    public InventoryService(CatalogService catalogService, InventoryRepository inventoryRepository) {
        this.catalogService = catalogService;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public InventoryResponse addStock(UUID productId, AddStockRequest request) {
        catalogService.getRequired(productId);
        InventoryItem item = inventoryRepository.findById(productId)
                .orElseGet(() -> inventoryRepository.save(new InventoryItem(productId, 0)));
        item.addStock(request.quantity());
        return InventoryResponse.from(item);
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(UUID productId) {
        return InventoryResponse.from(getRequired(productId));
    }

    public InventoryItem getRequired(UUID productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Inventory not found for product: " + productId));
    }
}
