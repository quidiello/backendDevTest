package com.nunegal.similarproducts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nunegal.similarproducts.domain.ProductDetail;
import com.nunegal.similarproducts.domain.ProductNotFoundException;
import com.nunegal.similarproducts.domain.ProductRepository;
import com.nunegal.similarproducts.domain.ProductServiceUnavailableException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimilarProductsServiceTest {

    private static final ProductDetail DRESS = new ProductDetail("2", "Dress", new BigDecimal("19.99"), true);
    private static final ProductDetail BLAZER = new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false);
    private static final ProductDetail BOOTS = new ProductDetail("4", "Boots", new BigDecimal("39.99"), true);

    @Mock
    private ProductRepository productRepository;

    private SimilarProductsService service;

    @BeforeEach
    void setUp() {
        service = new SimilarProductsService(productRepository);
    }

    @Test
    void returnsTheSimilarProductsInTheOrderOfTheSimilarIds() {
        when(productRepository.findSimilarIds("1")).thenReturn(List.of("4", "2", "3"));
        when(productRepository.findById("2")).thenReturn(Optional.of(DRESS));
        when(productRepository.findById("3")).thenReturn(Optional.of(BLAZER));
        when(productRepository.findById("4")).thenReturn(Optional.of(BOOTS));

        assertThat(service.findSimilarProducts("1")).containsExactly(BOOTS, DRESS, BLAZER);
    }

    @Test
    void returnsAnEmptyListWhenTheProductHasNoSimilarProducts() {
        when(productRepository.findSimilarIds("1")).thenReturn(List.of());

        assertThat(service.findSimilarProducts("1")).isEmpty();
    }

    @Test
    void skipsSimilarProductsThatDoNotExist() {
        when(productRepository.findSimilarIds("4")).thenReturn(List.of("2", "5", "3"));
        when(productRepository.findById("2")).thenReturn(Optional.of(DRESS));
        when(productRepository.findById("5")).thenReturn(Optional.empty());
        when(productRepository.findById("3")).thenReturn(Optional.of(BLAZER));

        assertThat(service.findSimilarProducts("4")).containsExactly(DRESS, BLAZER);
    }

    @Test
    void skipsSimilarProductsWhoseDetailIsUnavailable() {
        when(productRepository.findSimilarIds("5")).thenReturn(List.of("2", "6", "3"));
        when(productRepository.findById("2")).thenReturn(Optional.of(DRESS));
        when(productRepository.findById("6"))
                .thenThrow(new ProductServiceUnavailableException("boom", new RuntimeException()));
        when(productRepository.findById("3")).thenReturn(Optional.of(BLAZER));

        assertThat(service.findSimilarProducts("5")).containsExactly(DRESS, BLAZER);
    }

    @Test
    void throwsNotFoundWhenTheProductDoesNotExist() {
        when(productRepository.findSimilarIds("7")).thenThrow(new ProductNotFoundException("7"));

        assertThatThrownBy(() -> service.findSimilarProducts("7"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void throwsUnavailableWhenTheSimilarIdsCannotBeRetrieved() {
        when(productRepository.findSimilarIds("1"))
                .thenThrow(new ProductServiceUnavailableException("boom", new RuntimeException()));

        assertThatThrownBy(() -> service.findSimilarProducts("1"))
                .isInstanceOf(ProductServiceUnavailableException.class);
    }
}
