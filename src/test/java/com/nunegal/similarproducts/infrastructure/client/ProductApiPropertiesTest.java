package com.nunegal.similarproducts.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProductApiPropertiesTest {

    private static final URI BASE_URL = URI.create("http://localhost:3001");
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsTheDefaultConfiguration() {
        var properties = new ProductApiProperties(BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(6), 600,
                ONE_SECOND, Duration.ofSeconds(30));

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsAReadTimeoutOfZero() {
        var properties = new ProductApiProperties(BASE_URL, ONE_SECOND, Duration.ZERO, 600, ONE_SECOND, ONE_SECOND);

        assertThat(validator.validate(properties)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("readTimeout");
    }

    @Test
    void rejectsAConnectTimeoutOfZero() {
        var properties = new ProductApiProperties(BASE_URL, Duration.ZERO, ONE_SECOND, 600, ONE_SECOND, ONE_SECOND);

        assertThat(validator.validate(properties)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("connectTimeout");
    }

    @Test
    void rejectsNegativeWaitAndFailureTtl() {
        var negative = Duration.ofSeconds(-1);
        var properties = new ProductApiProperties(BASE_URL, ONE_SECOND, ONE_SECOND, 600, negative, negative);

        assertThat(validator.validate(properties)).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("maxWaitForSlot", "failureTtl");
    }

    @Test
    void allowsZeroToDisableRememberingFailures() {
        var properties = new ProductApiProperties(BASE_URL, ONE_SECOND, ONE_SECOND, 600, ONE_SECOND, Duration.ZERO);

        assertThat(validator.validate(properties)).isEmpty();
    }
}
