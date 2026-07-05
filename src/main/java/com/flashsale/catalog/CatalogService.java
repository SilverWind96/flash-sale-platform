package com.flashsale.catalog;

import com.flashsale.common.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CatalogService {

    private final ProductRepository productRepository;

    public CatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        try {
            Product product = productRepository.save(new Product(request.sku(), request.name(), request.priceCents()));
            return ProductResponse.from(product);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("PRODUCT_SKU_ALREADY_EXISTS", HttpStatus.CONFLICT,
                    "Product SKU already exists: " + request.sku());
        }
    }

    @Transactional(readOnly = true)
    public Product getRequired(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Product not found: " + productId));
    }
}
