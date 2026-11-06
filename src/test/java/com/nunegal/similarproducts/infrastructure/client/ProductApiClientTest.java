package com.nunegal.similarproducts.infrastructure.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.nunegal.similarproducts.domain.ProductDetail;
import com.nunegal.similarproducts.domain.ProductNotFoundException;
import com.nunegal.similarproducts.domain.ProductServiceUnavailableException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class ProductApiClientTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(500);
    private static final Duration MAX_WAIT_FOR_SLOT = Duration.ofMillis(100);

    private URI baseUrl;
    private ProductApiClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        baseUrl = URI.create(wireMock.getHttpBaseUrl());
        client = clientWithMaxConcurrentRequests(10);
    }

    @Test
    void returnsSimilarIdsInTheOrderTheApiSendsThem() {
        stubFor(get("/product/1/similarids").willReturn(okJson("[\"3\",\"2\",\"4\"]")));

        assertThat(client.findSimilarIds("1")).containsExactly("3", "2", "4");
    }

    @Test
    void readsSimilarIdsSentAsNumbers() {
        stubFor(get("/product/1/similarids").willReturn(okJson("[2,3,4]")));

        assertThat(client.findSimilarIds("1")).containsExactly("2", "3", "4");
    }

    @Test
    void throwsNotFoundWhenTheProductHasNoSimilarIds() {
        stubFor(get("/product/7/similarids").willReturn(notFound()));

        assertThatThrownBy(() -> client.findSimilarIds("7"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void throwsUnavailableWhenSimilarIdsFailWithServerError() {
        stubFor(get("/product/1/similarids").willReturn(serverError()));

        assertThatThrownBy(() -> client.findSimilarIds("1"))
                .isInstanceOf(ProductServiceUnavailableException.class);
    }

    @Test
    void throwsUnavailableWhenSimilarIdsTakeLongerThanTheReadTimeout() {
        stubFor(get("/product/1/similarids").willReturn(okJson("[2,3,4]")
                .withFixedDelay((int) READ_TIMEOUT.multipliedBy(2).toMillis())));

        assertThatThrownBy(() -> client.findSimilarIds("1"))
                .isInstanceOf(ProductServiceUnavailableException.class);
    }

    @Test
    void throwsUnavailableWhenSimilarIdsAnswerWithAnUnexpectedClientError() {
        stubFor(get("/product/1/similarids").willReturn(badRequest()));

        assertThatThrownBy(() -> client.findSimilarIds("1"))
                .isInstanceOf(ProductServiceUnavailableException.class);
    }

    @Test
    void throwsUnavailableWhenTheConnectionIsReset() {
        stubFor(get("/product/1/similarids").willReturn(aResponse()
                .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.findSimilarIds("1"))
                .isInstanceOf(ProductServiceUnavailableException.class);
    }

    @Test
    void returnsTheProductDetail() {
        stubFor(get("/product/1").willReturn(okJson(
                "{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}")));

        assertThat(client.findById("1"))
                .contains(new ProductDetail("1", "Shirt", new BigDecimal("9.99"), true));
    }

    @Test
    void returnsEmptyWhenTheProductDoesNotExist() {
        stubFor(get("/product/5").willReturn(notFound()));

        assertThat(client.findById("5")).isEmpty();
    }

    @Test
    void throwsUnavailableWhenTheProductDetailFailsWithServerError() {
        stubFor(get("/product/6").willReturn(serverError()));

        assertThatThrownBy(() -> client.findById("6"))
                .isInstanceOf(ProductServiceUnavailableException.class);
    }

    @Test
    void throwsUnavailableWhenTheProductDetailTakesLongerThanTheReadTimeout() {
        stubFor(get("/product/100").willReturn(aResponse()
                .withFixedDelay((int) READ_TIMEOUT.multipliedBy(2).toMillis())
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"100\",\"name\":\"Trousers\",\"price\":49.99,\"availability\":false}")));

        assertThatThrownBy(() -> client.findById("100"))
                .isInstanceOf(ProductServiceUnavailableException.class);
    }

    @Test
    void throwsUnavailableWhenNoSlotIsFreedWithinTheMaxWait() throws InterruptedException {
        var singleSlotClient = clientWithMaxConcurrentRequests(1);
        stubFor(get("/product/100").willReturn(okJson(
                "{\"id\":\"100\",\"name\":\"Trousers\",\"price\":49.99,\"availability\":false}")
                .withFixedDelay((int) MAX_WAIT_FOR_SLOT.multipliedBy(3).toMillis())));
        CompletableFuture.runAsync(() -> singleSlotClient.findById("100"));
        awaitRequestTo("/product/100");

        assertThatThrownBy(() -> singleSlotClient.findById("100"))
                .isInstanceOf(ProductServiceUnavailableException.class);
    }

    @Test
    void releasesTheSlotWhenTheCallFinishes() {
        var singleSlotClient = clientWithMaxConcurrentRequests(1);
        stubFor(get("/product/1").willReturn(okJson(
                "{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}")));

        singleSlotClient.findById("1");

        assertThat(singleSlotClient.findById("1")).isPresent();
    }

    @Test
    void releasesTheSlotWhenTheCallFails() {
        var singleSlotClient = clientWithMaxConcurrentRequests(1);
        stubFor(get("/product/6").willReturn(serverError()));
        stubFor(get("/product/1").willReturn(okJson(
                "{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}")));

        assertThatThrownBy(() -> singleSlotClient.findById("6"))
                .isInstanceOf(ProductServiceUnavailableException.class);

        assertThat(singleSlotClient.findById("1")).isPresent();
    }

    private ProductApiClient clientWithMaxConcurrentRequests(int maxConcurrentRequests) {
        var properties = new ProductApiProperties(
                baseUrl, Duration.ofSeconds(1), READ_TIMEOUT, maxConcurrentRequests, MAX_WAIT_FOR_SLOT);
        return new ProductApiClient(RestClient.builder(), properties);
    }

    private static void awaitRequestTo(String url) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (findAll(getRequestedFor(urlEqualTo(url))).isEmpty()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("No request reached " + url);
            }
            Thread.sleep(10);
        }
    }
}
