package com.flashsale.order;

import com.flashsale.catalog.CatalogService;
import com.flashsale.catalog.CreateProductRequest;
import com.flashsale.catalog.ProductResponse;
import com.flashsale.common.BusinessException;
import com.flashsale.inventory.AddStockRequest;
import com.flashsale.inventory.InventoryItem;
import com.flashsale.inventory.InventoryRepository;
import com.flashsale.inventory.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CheckoutConcurrencyTest {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CheckoutService checkoutService;

    @Test
    void checkoutWithSameIdempotencyKeyReturnsSameOrderOnce() {
        ProductResponse product = createProductWithStock(3);

        CheckoutCommand command = new CheckoutCommand(product.id(), 1, "same-key-" + UUID.randomUUID());

        CheckoutResponse first = checkoutService.checkout(command);
        CheckoutResponse second = checkoutService.checkout(command);

        InventoryItem inventory = inventoryRepository.findById(product.id()).orElseThrow();

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(inventory.available()).isEqualTo(2);
        assertThat(inventory.reserved()).isEqualTo(1);
    }

    @Test
    void concurrentCheckoutDoesNotOversellInventory() throws Exception {
        int stock = 10;
        int attempts = 40;
        ProductResponse product = createProductWithStock(stock);

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<AttemptResult>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            int attempt = i;
            futures.add(executor.submit(checkoutAttempt(product.id(), attempt, ready, start)));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<AttemptResult> results = new ArrayList<>();
        for (Future<AttemptResult> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        long successfulReservations = results.stream().filter(AttemptResult::success).count();
        InventoryItem inventory = inventoryRepository.findById(product.id()).orElseThrow();
        long persistedOrders = orderRepository.findAll().stream()
                .filter(order -> order.productId().equals(product.id()))
                .count();

        assertThat(successfulReservations).isLessThanOrEqualTo(stock);
        assertThat(persistedOrders).isEqualTo(successfulReservations);
        assertThat(inventory.available()).isEqualTo(stock - successfulReservations);
        assertThat(inventory.reserved()).isEqualTo(successfulReservations);
        assertThat(inventory.available()).isNotNegative();
    }

    private Callable<AttemptResult> checkoutAttempt(UUID productId,
                                                    int attempt,
                                                    CountDownLatch ready,
                                                    CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                CheckoutResponse response = checkoutService.checkout(new CheckoutCommand(
                        productId,
                        1,
                        "checkout-" + productId + "-" + attempt
                ));
                return AttemptResult.success(response.orderId());
            } catch (BusinessException exception) {
                return AttemptResult.failure(exception.code());
            }
        };
    }

    private ProductResponse createProductWithStock(int stock) {
        String suffix = UUID.randomUUID().toString();
        ProductResponse product = catalogService.createProduct(new CreateProductRequest(
                "SKU-" + suffix,
                "Concert Ticket " + suffix,
                2_500
        ));
        inventoryService.addStock(product.id(), new AddStockRequest(stock));
        return product;
    }

    private record AttemptResult(boolean success, UUID orderId, String failureCode) {

        static AttemptResult success(UUID orderId) {
            return new AttemptResult(true, orderId, null);
        }

        static AttemptResult failure(String failureCode) {
            return new AttemptResult(false, null, failureCode);
        }
    }
}
