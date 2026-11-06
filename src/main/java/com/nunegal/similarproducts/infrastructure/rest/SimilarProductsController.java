package com.nunegal.similarproducts.infrastructure.rest;

import com.nunegal.similarproducts.application.SimilarProductsService;
import com.nunegal.similarproducts.domain.ProductDetail;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimilarProductsController {

    private final SimilarProductsService similarProductsService;

    public SimilarProductsController(SimilarProductsService similarProductsService) {
        this.similarProductsService = similarProductsService;
    }

    @GetMapping("/product/{productId}/similar")
    public List<ProductDetail> getSimilarProducts(@PathVariable String productId) {
        return similarProductsService.findSimilarProducts(productId);
    }
}
