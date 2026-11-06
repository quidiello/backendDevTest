package com.nunegal.similarproducts.application;

import com.nunegal.similarproducts.domain.ProductDetail;
import com.nunegal.similarproducts.domain.ProductRepository;
import com.nunegal.similarproducts.domain.ProductServiceUnavailableException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SimilarProductsService {

    private static final Logger log = LoggerFactory.getLogger(SimilarProductsService.class);

    private final ProductRepository productRepository;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SimilarProductsService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductDetail> findSimilarProducts(String productId) {
        // Collected before joining so every request is started before waiting for any of them.
        var requests = productRepository.findSimilarIds(productId).stream()
                .map(id -> CompletableFuture.supplyAsync(() -> findSimilarProduct(id), executor))
                .toList();

        return requests.stream()
                .map(CompletableFuture::join)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<ProductDetail> findSimilarProduct(String productId) {
        try {
            var product = productRepository.findById(productId);
            if (product.isEmpty()) {
                log.warn("Skipping similar product {}: not found", productId);
            }
            return product;
        } catch (ProductServiceUnavailableException e) {
            log.warn("Skipping similar product {}: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }
}
