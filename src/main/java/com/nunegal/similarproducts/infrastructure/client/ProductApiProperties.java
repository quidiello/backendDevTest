package com.nunegal.similarproducts.infrastructure.client;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("product-api")
public record ProductApiProperties(
        @NotNull URI baseUrl,
        @NotNull @DurationMin(millis = 1) Duration connectTimeout,
        @NotNull @DurationMin(millis = 1) Duration readTimeout,
        @Positive int maxConcurrentRequests,
        @NotNull @DurationMin(millis = 0) Duration maxWaitForSlot,
        @NotNull @DurationMin(millis = 0) Duration failureTtl) {
}
