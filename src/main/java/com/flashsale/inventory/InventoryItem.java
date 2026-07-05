package com.flashsale.inventory;

import com.flashsale.common.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    private UUID productId;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    @Version
    @Column(nullable = false)
    private long version;

    protected InventoryItem() {
    }

    public InventoryItem(UUID productId, int initialStock) {
        if (initialStock < 0) {
            throw new IllegalArgumentException("initialStock must be non-negative");
        }
        this.productId = productId;
        this.available = initialStock;
        this.reserved = 0;
    }

    public void addStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("INVALID_STOCK_QUANTITY", HttpStatus.BAD_REQUEST,
                    "Stock quantity must be positive");
        }
        this.available += quantity;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("INVALID_RESERVATION_QUANTITY", HttpStatus.BAD_REQUEST,
                    "Reservation quantity must be positive");
        }
        if (available < quantity) {
            throw new BusinessException("INSUFFICIENT_STOCK", HttpStatus.CONFLICT,
                    "Not enough stock available");
        }
        this.available -= quantity;
        this.reserved += quantity;
    }

    public UUID productId() {
        return productId;
    }

    public int available() {
        return available;
    }

    public int reserved() {
        return reserved;
    }

    public long version() {
        return version;
    }
}
