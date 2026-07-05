package com.flashsale.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long priceCents;

    @Column(nullable = false)
    private Instant createdAt;

    protected Product() {
    }

    public Product(String sku, String name, long priceCents) {
        if (priceCents < 0) {
            throw new IllegalArgumentException("priceCents must be non-negative");
        }
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.name = name;
        this.priceCents = priceCents;
        this.createdAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public String sku() {
        return sku;
    }

    public String name() {
        return name;
    }

    public long priceCents() {
        return priceCents;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
