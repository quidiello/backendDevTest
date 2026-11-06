package com.nunegal.similarproducts.domain;

public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
