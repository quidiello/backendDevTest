package com.nunegal.similarproducts.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    List<String> findSimilarIds(String productId);

    Optional<ProductDetail> findById(String productId);
}
