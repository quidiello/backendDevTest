package com.nunegal.similarproducts.infrastructure.client;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nunegal.similarproducts.domain.ProductRepository;
import com.nunegal.similarproducts.domain.ProductServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductApiClientCacheTest {

    private static final String SHIRT = "{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}";

    @RegisterExtension
    static WireMockExtension productApi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void productApiProperties(DynamicPropertyRegistry registry) {
        registry.add("product-api.base-url", productApi::baseUrl);
        // Failures are remembered by the client itself; disabled here to test only the Spring cache.
        registry.add("product-api.failure-ttl", () -> "0s");
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void requestsTheProductDetailOnlyOnceWhileItIsCached() {
        productApi.stubFor(get("/product/1").willReturn(okJson(SHIRT)));

        productRepository.findById("1");
        productRepository.findById("1");

        productApi.verify(1, getRequestedFor(urlEqualTo("/product/1")));
    }

    @Test
    void requestsTheSimilarIdsOnlyOnceWhileTheyAreCached() {
        productApi.stubFor(get("/product/1/similarids").willReturn(okJson("[2,3,4]")));

        productRepository.findSimilarIds("1");
        productRepository.findSimilarIds("1");

        productApi.verify(1, getRequestedFor(urlEqualTo("/product/1/similarids")));
    }

    @Test
    void remembersThatAProductDoesNotExist() {
        productApi.stubFor(get("/product/5").willReturn(notFound()));

        assertThat(productRepository.findById("5")).isEmpty();
        assertThat(productRepository.findById("5")).isEmpty();

        productApi.verify(1, getRequestedFor(urlEqualTo("/product/5")));
    }

    @Test
    void doesNotRememberFailures() {
        productApi.stubFor(get("/product/1").willReturn(serverError()));
        assertThatThrownBy(() -> productRepository.findById("1"))
                .isInstanceOf(ProductServiceUnavailableException.class);

        productApi.stubFor(get("/product/1").willReturn(okJson(SHIRT)));

        assertThat(productRepository.findById("1")).isPresent();
        productApi.verify(2, getRequestedFor(urlEqualTo("/product/1")));
    }
}
