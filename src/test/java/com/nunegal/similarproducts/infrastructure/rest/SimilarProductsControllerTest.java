package com.nunegal.similarproducts.infrastructure.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nunegal.similarproducts.application.SimilarProductsService;
import com.nunegal.similarproducts.domain.ProductDetail;
import com.nunegal.similarproducts.domain.ProductNotFoundException;
import com.nunegal.similarproducts.domain.ProductServiceUnavailableException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SimilarProductsController.class)
class SimilarProductsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimilarProductsService service;

    @Test
    void returnsTheSimilarProductsAsDefinedInTheContract() throws Exception {
        when(service.findSimilarProducts("1")).thenReturn(List.of(
                new ProductDetail("2", "Dress", new BigDecimal("19.99"), true),
                new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false)));

        mockMvc.perform(get("/product/1/similar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        [
                          {"id": "2", "name": "Dress", "price": 19.99, "availability": true},
                          {"id": "3", "name": "Blazer", "price": 29.99, "availability": false}
                        ]
                        """, true));
    }

    @Test
    void returnsNotFoundProblemWhenTheProductDoesNotExist() throws Exception {
        when(service.findSimilarProducts("7")).thenThrow(new ProductNotFoundException("7"));

        mockMvc.perform(get("/product/7/similar"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Product not found: 7"));
    }

    @Test
    void returnsBadGatewayProblemWhenTheProductServiceIsUnavailable() throws Exception {
        when(service.findSimilarProducts("1"))
                .thenThrow(new ProductServiceUnavailableException("boom", new RuntimeException()));

        mockMvc.perform(get("/product/1/similar"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(502));
    }
}
