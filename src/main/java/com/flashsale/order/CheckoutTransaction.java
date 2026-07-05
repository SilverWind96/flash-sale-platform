package com.flashsale.order;

import com.flashsale.catalog.CatalogService;
import com.flashsale.catalog.Product;
import com.flashsale.inventory.InventoryItem;
import com.flashsale.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CheckoutTransaction {

    private static final Logger log = LoggerFactory.getLogger(CheckoutTransaction.class);

    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    CheckoutTransaction(CatalogService catalogService,
                        InventoryService inventoryService,
                        OrderRepository orderRepository) {
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public CheckoutResponse checkoutOnce(CheckoutCommand command) {
        return orderRepository.findByIdempotencyKey(command.idempotencyKey())
                .map(CheckoutResponse::from)
                .orElseGet(() -> createOrder(command));
    }

    @Transactional(readOnly = true)
    public CheckoutResponse findByIdempotencyKey(String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(CheckoutResponse::from)
                .orElseThrow();
    }

    private CheckoutResponse createOrder(CheckoutCommand command) {
        Product product = catalogService.getRequired(command.productId());
        InventoryItem inventory = inventoryService.getRequired(command.productId());

        inventory.reserve(command.quantity());

        CustomerOrder order = new CustomerOrder(
                command.productId(),
                command.quantity(),
                product.priceCents() * command.quantity(),
                command.idempotencyKey()
        );

        CustomerOrder savedOrder = orderRepository.saveAndFlush(order);
        log.info("checkoutReserved orderId={} productId={} quantity={} amountCents={} idempotencyKey={}",
                savedOrder.id(),
                savedOrder.productId(),
                savedOrder.quantity(),
                savedOrder.amountCents(),
                savedOrder.idempotencyKey());
        return CheckoutResponse.from(savedOrder);
    }
}
