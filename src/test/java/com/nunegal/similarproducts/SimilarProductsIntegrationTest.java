package com.nunegal.similarproducts;

import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SimilarProductsIntegrationTest {

    @RegisterExtension
    static WireMockExtension productApi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void productApiProperties(DynamicPropertyRegistry registry) {
        registry.add("product-api.base-url", productApi::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void stubProductApi() {
        stubSimilarIds("1", "[2,3,4]");
        stubSimilarIds("4", "[1,2,5]");
        stubSimilarIds("5", "[1,2,6]");
        productApi.stubFor(WireMock.get("/product/7/similarids").willReturn(notFound()));
        productApi.stubFor(WireMock.get("/product/9/similarids").willReturn(serverError()));

        stubProduct("1", "Shirt", "9.99", true);
        stubProduct("2", "Dress", "19.99", true);
        stubProduct("3", "Blazer", "29.99", false);
        stubProduct("4", "Boots", "39.99", true);
        productApi.stubFor(WireMock.get("/product/5").willReturn(notFound()));
        productApi.stubFor(WireMock.get("/product/6").willReturn(serverError()));
    }

    @Test
    void returnsTheDetailOfEverySimilarProductInOrder() throws Exception {
        mockMvc.perform(get("/product/1/similar"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"id": "2", "name": "Dress", "price": 19.99, "availability": true},
                          {"id": "3", "name": "Blazer", "price": 29.99, "availability": false},
                          {"id": "4", "name": "Boots", "price": 39.99, "availability": true}
                        ]
                        """, true));
    }

    @Test
    void leavesOutASimilarProductThatDoesNotExist() throws Exception {
        mockMvc.perform(get("/product/4/similar"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"id": "1", "name": "Shirt", "price": 9.99, "availability": true},
                          {"id": "2", "name": "Dress", "price": 19.99, "availability": true}
                        ]
                        """, true));
    }

    @Test
    void leavesOutASimilarProductWhoseDetailFails() throws Exception {
        mockMvc.perform(get("/product/5/similar"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"id": "1", "name": "Shirt", "price": 9.99, "availability": true},
                          {"id": "2", "name": "Dress", "price": 19.99, "availability": true}
                        ]
                        """, true));
    }

    @Test
    void returnsNotFoundWhenTheProductDoesNotExist() throws Exception {
        mockMvc.perform(get("/product/7/similar"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsBadGatewayWhenTheSimilarIdsCannotBeRetrieved() throws Exception {
        mockMvc.perform(get("/product/9/similar"))
                .andExpect(status().isBadGateway());
    }

    private static void stubSimilarIds(String productId, String similarIds) {
        productApi.stubFor(WireMock.get("/product/" + productId + "/similarids").willReturn(okJson(similarIds)));
    }

    private static void stubProduct(String id, String name, String price, boolean availability) {
        productApi.stubFor(WireMock.get("/product/" + id).willReturn(okJson("""
                {"id": "%s", "name": "%s", "price": %s, "availability": %s}
                """.formatted(id, name, price, availability))));
    }
}
