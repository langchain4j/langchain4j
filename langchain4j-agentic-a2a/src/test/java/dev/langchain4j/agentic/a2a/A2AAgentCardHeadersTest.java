package dev.langchain4j.agentic.a2a;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.A2AClientHTTPError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class A2AAgentCardHeadersTest {

    private static final String CARD_PATH = "/.well-known/agent-card.json";
    private WireMockServer server;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "tenant-a"})
    void should_obtain_headers_lazily_and_cache_the_card(String tenant) {
        String path = tenant.isEmpty() ? CARD_PATH : "/.well-known/" + tenant + "/agent-card.json";
        stubCard(path, "Bearer fresh-token");
        AtomicReference<String> token = new AtomicReference<>("expired-token");
        AtomicInteger supplierCalls = new AtomicInteger();
        AtomicInteger customizerCalls = new AtomicInteger();

        UntypedAgent agent = AgenticServices.a2aBuilder(server.baseUrl(), tenant)
                .agentCardHeaders(() -> {
                    supplierCalls.incrementAndGet();
                    return Map.of("Authorization", "Bearer " + token.get());
                })
                .clientCustomizer(ignored -> customizerCalls.incrementAndGet())
                .inputKeys("request")
                .build();

        assertThat(supplierCalls).hasValue(0);
        assertThat(server.getAllServeEvents()).isEmpty();
        token.set("fresh-token");

        A2AClientInstance instance = (A2AClientInstance) agent;
        assertThat(instance.agentCard().name()).isEqualTo("protected-agent");
        assertThat(instance.agentCard().name()).isEqualTo("protected-agent");

        assertThat(supplierCalls).hasValue(1);
        assertThat(customizerCalls).hasValue(0);
        server.verify(1, getRequestedFor(urlEqualTo(path)).withHeader("Authorization", equalTo("Bearer fresh-token")));
    }

    @Test
    void should_obtain_fresh_headers_when_retrying_a_failed_fetch() {
        server.stubFor(get(urlEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(401)));
        stubCard(CARD_PATH, "Bearer refreshed-token");
        AtomicReference<String> token = new AtomicReference<>("expired-token");
        AtomicInteger supplierCalls = new AtomicInteger();
        A2AClientInstance agent = (A2AClientInstance) AgenticServices.a2aBuilder(server.baseUrl())
                .agentCardHeaders(() -> {
                    supplierCalls.incrementAndGet();
                    return Map.of("Authorization", "Bearer " + token.get());
                })
                .inputKeys("request")
                .build();

        assertThatThrownBy(agent::agentCard)
                .isInstanceOf(A2AAgentCardException.class)
                .rootCause()
                .isInstanceOfSatisfying(
                        A2AClientHTTPError.class,
                        error -> assertThat(error.getCode()).isEqualTo(401));

        token.set("refreshed-token");
        assertThat(agent.agentCard().name()).isEqualTo("protected-agent");
        assertThat(supplierCalls).hasValue(2);
        server.verify(
                1, getRequestedFor(urlEqualTo(CARD_PATH)).withHeader("Authorization", equalTo("Bearer expired-token")));
        server.verify(
                1,
                getRequestedFor(urlEqualTo(CARD_PATH)).withHeader("Authorization", equalTo("Bearer refreshed-token")));
    }

    @ParameterizedTest
    @MethodSource("noHeaders")
    void should_allow_public_cards_without_headers(Supplier<Map<String, String>> headers) {
        server.stubFor(get(urlEqualTo(CARD_PATH)).willReturn(cardResponse()));
        A2AClientInstance agent = (A2AClientInstance) AgenticServices.a2aBuilder(server.baseUrl())
                .agentCardHeaders(headers)
                .inputKeys("request")
                .build();

        assertThat(agent.agentCard().name()).isEqualTo("protected-agent");

        server.verify(1, getRequestedFor(urlEqualTo(CARD_PATH)).withoutHeader("Authorization"));
    }

    static Stream<Supplier<Map<String, String>>> noHeaders() {
        return Stream.of(null, () -> null, Map::of);
    }

    @Test
    void should_preserve_unauthenticated_discovery_by_default() {
        server.stubFor(get(urlEqualTo(CARD_PATH)).willReturn(cardResponse()));
        A2AClientInstance agent = (A2AClientInstance) AgenticServices.a2aBuilder(server.baseUrl())
                .inputKeys("request")
                .build();

        assertThat(agent.agentCard().name()).isEqualTo("protected-agent");

        server.verify(1, getRequestedFor(urlEqualTo(CARD_PATH)).withoutHeader("Authorization"));
    }

    @Test
    void should_retry_after_a_header_supplier_failure_without_sending_a_request() {
        stubCard(CARD_PATH, "Bearer fresh-token");
        AtomicInteger supplierCalls = new AtomicInteger();
        IllegalStateException tokenFailure = new IllegalStateException("Token unavailable");
        A2AClientInstance agent = (A2AClientInstance) AgenticServices.a2aBuilder(server.baseUrl())
                .agentCardHeaders(() -> {
                    if (supplierCalls.incrementAndGet() == 1) {
                        throw tokenFailure;
                    }
                    return Map.of("Authorization", "Bearer fresh-token");
                })
                .inputKeys("request")
                .build();

        assertThatThrownBy(agent::agentCard)
                .isInstanceOf(A2AAgentCardException.class)
                .hasCause(tokenFailure);
        assertThat(server.getAllServeEvents()).isEmpty();

        assertThat(agent.agentCard().name()).isEqualTo("protected-agent");
        assertThat(supplierCalls).hasValue(2);
        server.verify(
                1, getRequestedFor(urlEqualTo(CARD_PATH)).withHeader("Authorization", equalTo("Bearer fresh-token")));
    }

    @Test
    void should_send_headers_to_the_sdk_fallback_card_url() {
        server.stubFor(get(urlEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(404)));
        stubCard("/", "Bearer fresh-token");
        A2AClientInstance agent = (A2AClientInstance) AgenticServices.a2aBuilder(server.baseUrl())
                .agentCardHeaders(() -> Map.of("Authorization", "Bearer fresh-token"))
                .inputKeys("request")
                .build();

        assertThat(agent.agentCard().name()).isEqualTo("protected-agent");

        server.verify(
                1, getRequestedFor(urlEqualTo(CARD_PATH)).withHeader("Authorization", equalTo("Bearer fresh-token")));
        server.verify(1, getRequestedFor(urlEqualTo("/")).withHeader("Authorization", equalTo("Bearer fresh-token")));
    }

    private void stubCard(String path, String authorization) {
        server.stubFor(get(urlEqualTo(path))
                .withHeader("Authorization", equalTo(authorization))
                .willReturn(cardResponse()));
    }

    private ResponseDefinitionBuilder cardResponse() {
        return aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                          "name": "protected-agent",
                          "description": "A protected remote agent",
                          "url": "%s",
                          "version": "1.0.0",
                          "capabilities": {},
                          "defaultInputModes": ["text"],
                          "defaultOutputModes": ["text"],
                          "skills": []
                        }
                        """.formatted(server.baseUrl()));
    }
}
