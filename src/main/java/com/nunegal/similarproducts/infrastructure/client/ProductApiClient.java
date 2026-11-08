package com.nunegal.similarproducts.infrastructure.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nunegal.similarproducts.domain.ProductDetail;
import com.nunegal.similarproducts.domain.ProductNotFoundException;
import com.nunegal.similarproducts.domain.ProductRepository;
import com.nunegal.similarproducts.domain.ProductServiceUnavailableException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ProductApiClient implements ProductRepository {

    private static final ParameterizedTypeReference<List<String>> ID_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final Semaphore slots;
    private final Duration maxWaitForSlot;
    private final Cache<String, Boolean> recentlyFailed;

    public ProductApiClient(RestClient.Builder restClientBuilder, ProductApiProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        this.slots = new Semaphore(properties.maxConcurrentRequests());
        this.maxWaitForSlot = properties.maxWaitForSlot();
        this.recentlyFailed = Caffeine.newBuilder()
                .expireAfterWrite(properties.failureTtl())
                .build();
    }

    @Override
    @Cacheable(cacheNames = "similar-ids")
    public List<String> findSimilarIds(String productId) {
        acquireSlot();
        try {
            return restClient.get()
                    .uri("/product/{productId}/similarids", productId)
                    .retrieve()
                    .body(ID_LIST);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(productId);
        } catch (RestClientException e) {
            throw new ProductServiceUnavailableException("Could not get similar ids for product " + productId, e);
        } finally {
            slots.release();
        }
    }

    @Override
    @Cacheable(cacheNames = "product-details")
    public Optional<ProductDetail> findById(String productId) {
        if (recentlyFailed.getIfPresent(productId) != null) {
            throw new ProductServiceUnavailableException("Detail of product " + productId + " failed recently");
        }
        acquireSlot();
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/product/{productId}", productId)
                    .retrieve()
                    .body(ProductDetail.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            recentlyFailed.put(productId, true);
            throw new ProductServiceUnavailableException("Could not get detail of product " + productId, e);
        } finally {
            slots.release();
        }
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(maxWaitForSlot.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ProductServiceUnavailableException("No free slot to call the product API");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProductServiceUnavailableException("Interrupted while waiting for a slot", e);
        }
    }
}
