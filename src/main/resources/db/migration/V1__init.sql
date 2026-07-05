CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    price_cents BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT chk_products_price_cents_non_negative CHECK (price_cents >= 0)
);

CREATE TABLE inventory_items (
    product_id UUID PRIMARY KEY,
    available INTEGER NOT NULL,
    reserved INTEGER NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_inventory_available_non_negative CHECK (available >= 0),
    CONSTRAINT chk_inventory_reserved_non_negative CHECK (reserved >= 0)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    amount_cents BIGINT NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_orders_amount_cents_non_negative CHECK (amount_cents >= 0)
);

CREATE INDEX idx_orders_product_id ON orders (product_id);
