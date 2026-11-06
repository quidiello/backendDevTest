package com.nunegal.similarproducts.infrastructure.client;

import com.nunegal.similarproducts.domain.ProductDetail;
import com.nunegal.similarproducts.domain.ProductNotFoundException;
import com.nunegal.similarproducts.domain.ProductRepository;
import com.nunegal.similarproducts.domain.ProductServiceUnavailableException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
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
    }

    @Override
    public List<String> findSimilarIds(String productId) {
        try {
            return restClient.get()
                    .uri("/product/{productId}/similarids", productId)
                    .retrieve()
                    .body(ID_LIST);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(productId);
        } catch (RestClientException e) {
            throw new ProductServiceUnavailableException("Could not get similar ids for product " + productId, e);
        }
    }

    @Override
    public Optional<ProductDetail> findById(String productId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/product/{productId}", productId)
                    .retrieve()
                    .body(ProductDetail.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ProductServiceUnavailableException("Could not get detail of product " + productId, e);
        }
    }
}
