package com.flashsale.order;

import com.flashsale.common.BusinessException;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.NoSuchElementException;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private static final int MAX_OPTIMISTIC_RETRIES = 8;
    private static final Duration BASE_BACKOFF = Duration.ofMillis(10);

    private final CheckoutTransaction checkoutTransaction;

    CheckoutService(CheckoutTransaction checkoutTransaction) {
        this.checkoutTransaction = checkoutTransaction;
    }

    public CheckoutResponse checkout(CheckoutCommand command) {
        validate(command);

        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_RETRIES; attempt++) {
            try {
                return checkoutTransaction.checkoutOnce(command);
            } catch (DataIntegrityViolationException exception) {
                log.info("checkoutIdempotencyRace productId={} idempotencyKey={}",
                        command.productId(),
                        command.idempotencyKey());
                return findExistingOrder(command.idempotencyKey(), exception);
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
                if (attempt == MAX_OPTIMISTIC_RETRIES) {
                    log.warn("checkoutContentionExhausted productId={} quantity={} idempotencyKey={} attempts={}",
                            command.productId(),
                            command.quantity(),
                            command.idempotencyKey(),
                            MAX_OPTIMISTIC_RETRIES);
                    throw new BusinessException("CHECKOUT_CONTENTION", HttpStatus.CONFLICT,
                            "Checkout is under high contention; retry with the same idempotency key");
                }
                log.debug("checkoutOptimisticRetry productId={} idempotencyKey={} attempt={}",
                        command.productId(),
                        command.idempotencyKey(),
                        attempt);
                sleepBeforeRetry(attempt);
            }
        }

        throw new BusinessException("CHECKOUT_FAILED", HttpStatus.CONFLICT, "Checkout failed");
    }

    private CheckoutResponse findExistingOrder(String idempotencyKey, RuntimeException originalException) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                return checkoutTransaction.findByIdempotencyKey(idempotencyKey);
            } catch (NoSuchElementException ignored) {
                sleepBeforeRetry(attempt);
            }
        }
        throw originalException;
    }

    private void validate(CheckoutCommand command) {
        if (command.productId() == null) {
            throw new BusinessException("PRODUCT_REQUIRED", HttpStatus.BAD_REQUEST, "productId is required");
        }
        if (command.quantity() <= 0) {
            throw new BusinessException("INVALID_CHECKOUT_QUANTITY", HttpStatus.BAD_REQUEST,
                    "Checkout quantity must be positive");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required");
        }
        if (command.idempotencyKey().length() > 160) {
            throw new BusinessException("IDEMPOTENCY_KEY_TOO_LONG", HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be at most 160 characters");
        }
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF.multipliedBy(attempt).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("CHECKOUT_INTERRUPTED", HttpStatus.INTERNAL_SERVER_ERROR,
                    "Checkout was interrupted");
        }
    }
}
