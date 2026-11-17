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
import dev.langchain4j.agentic.declarative.A2AAgentCardHeaders;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;
import java.util.List;
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
    private static final AtomicReference<String> SERVER_URL = new AtomicReference<>();
    private static final AtomicReference<Map<String, String>> HEADERS = new AtomicReference<>();
    private static final AtomicInteger HEADER_CALLS = new AtomicInteger();
    private WireMockServer server;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(0);
        server.start();
        SERVER_URL.set(server.baseUrl());
        HEADERS.set(Map.of());
        HEADER_CALLS.set(0);
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

    public interface DeclarativeProtectedAgent {

        @A2AClientAgent(outputKey = "response")
        String invoke(@V("request") String request);

        @A2AServerUrlSupplier
        static String serverUrl() {
            return SERVER_URL.get();
        }

        @A2AAgentCardHeaders
        static Map<String, String> cardHeaders() {
            HEADER_CALLS.incrementAndGet();
            return HEADERS.get();
        }
    }

    @Test
    void should_fetch_declarative_headers_lazily_retry_and_cache_the_card() {
        server.stubFor(get(urlEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(401)));
        stubCard(CARD_PATH, "Bearer refreshed-token");
        HEADERS.set(Map.of("Authorization", "Bearer expired-token"));
        DeclarativeProtectedAgent agent =
                AgenticServices.createAgenticSystem(DeclarativeProtectedAgent.class, (ChatModel) null);
        A2AClientInstance client = (A2AClientInstance) agent;

        assertThat(HEADER_CALLS).hasValue(0);
        assertThat(server.getAllServeEvents()).isEmpty();
        assertThatThrownBy(client::agentCard)
                .isInstanceOf(A2AAgentCardException.class)
                .rootCause()
                .isInstanceOfSatisfying(
                        A2AClientHTTPError.class,
                        error -> assertThat(error.getCode()).isEqualTo(401));

        HEADERS.set(Map.of("Authorization", "Bearer refreshed-token"));
        assertThat(client.agentCard().name()).isEqualTo("protected-agent");
        assertThat(client.agentCard().name()).isEqualTo("protected-agent");
        assertThat(HEADER_CALLS).hasValue(2);
        server.verify(
                1, getRequestedFor(urlEqualTo(CARD_PATH)).withHeader("Authorization", equalTo("Bearer expired-token")));
        server.verify(
                1,
                getRequestedFor(urlEqualTo(CARD_PATH)).withHeader("Authorization", equalTo("Bearer refreshed-token")));
    }

    @ParameterizedTest
    @MethodSource("absentDeclarativeHeaders")
    void should_allow_declarative_headers_to_be_null_or_empty(Map<String, String> headers) {
        server.stubFor(get(urlEqualTo(CARD_PATH)).willReturn(cardResponse()));
        HEADERS.set(headers);
        var agent = AgenticServices.createAgenticSystem(DeclarativeProtectedAgent.class, (ChatModel) null);

        assertThat(((A2AClientInstance) agent).agentCard().name()).isEqualTo("protected-agent");
        server.verify(1, getRequestedFor(urlEqualTo(CARD_PATH)).withoutHeader("Authorization"));
    }

    static Stream<Map<String, String>> absentDeclarativeHeaders() {
        return Stream.of(null, Map.of());
    }

    public interface AgentWithHelper {

        @A2AClientAgent(outputKey = "response")
        String invoke(@V("request") String request);

        @A2AServerUrlSupplier
        static String serverUrl() {
            return SERVER_URL.get();
        }
    }

    public static class CardHeadersHelper {

        private String token = "expired-token";
        private int calls;

        @A2AAgentCardHeaders
        Map<String, String> cardHeaders() {
            calls++;
            return Map.of("Authorization", "Bearer " + token);
        }
    }

    @Test
    void should_invoke_card_headers_on_the_helper_object_when_the_card_is_needed() {
        stubCard(CARD_PATH, "Bearer helper-token");
        CardHeadersHelper helper = new CardHeadersHelper();
        var configurator = new AgenticServices.AgentConfigurator(ctx -> {}, null, null, null, type -> List.of(helper));
        var agent = AgenticServices.createAgenticSystem(AgentWithHelper.class, (ChatModel) null, configurator);

        assertThat(helper.calls).isZero();
        helper.token = "helper-token";
        assertThat(((A2AClientInstance) agent).agentCard().name()).isEqualTo("protected-agent");
        assertThat(helper.calls).isOne();
        server.verify(
                1, getRequestedFor(urlEqualTo(CARD_PATH)).withHeader("Authorization", equalTo("Bearer helper-token")));
    }

    public interface AgentWithResolvedHeaders {

        @A2AClientAgent(outputKey = "response")
        String invoke(@V("request") String request);

        @A2AServerUrlSupplier
        static String serverUrl() {
            return SERVER_URL.get();
        }

        @A2AAgentCardHeaders
        static Map<String, String> cardHeaders(Supplier<String> tokenProvider) {
            HEADER_CALLS.incrementAndGet();
            return Map.of("Authorization", "Bearer " + tokenProvider.get());
        }
    }

    @Test
    void should_resolve_parameters_at_build_time_but_invoke_the_provider_on_each_fetch_attempt() {
        server.stubFor(get(urlEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(401)));
        stubCard(CARD_PATH, "Bearer fresh-token");
        AtomicReference<String> token = new AtomicReference<>("expired-token");
        AtomicInteger resolutions = new AtomicInteger();
        SupplierParameterResolver resolver = new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.supplierMethod().isAnnotationPresent(A2AAgentCardHeaders.class)
                        && context.parameter().getType() == Supplier.class;
            }

            @Override
            public Object resolve(Context context) {
                resolutions.incrementAndGet();
                return (Supplier<String>) token::get;
            }
        };
        DeclarativeUtil.addSupplierParameterResolver(resolver);
        try {
            var agent = AgenticServices.createAgenticSystem(AgentWithResolvedHeaders.class, (ChatModel) null);
            A2AClientInstance client = (A2AClientInstance) agent;
            assertThat(resolutions).hasValue(1);
            assertThat(HEADER_CALLS).hasValue(0);
            assertThatThrownBy(client::agentCard)
                    .isInstanceOf(A2AAgentCardException.class)
                    .rootCause()
                    .isInstanceOf(A2AClientHTTPError.class);

            token.set("fresh-token");
            assertThat(client.agentCard().name()).isEqualTo("protected-agent");
            assertThat(resolutions).hasValue(1);
            assertThat(HEADER_CALLS).hasValue(2);
            server.verify(
                    1,
                    getRequestedFor(urlEqualTo(CARD_PATH)).withHeader("Authorization", equalTo("Bearer fresh-token")));
        } finally {
            DeclarativeUtil.getSupplierParameterResolvers().remove(resolver);
        }
    }

    public interface AgentWithNonStaticHeaders {

        @A2AClientAgent(a2aServerUrl = "http://localhost:8080", outputKey = "response")
        String invoke(@V("request") String request);

        @A2AAgentCardHeaders
        default Map<String, String> cardHeaders() {
            return Map.of();
        }
    }

    @Test
    void should_reject_a_non_static_header_method_without_a_helper_object() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(AgentWithNonStaticHeaders.class, (ChatModel) null))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("@A2AAgentCardHeaders")
                .hasMessageContaining("is not static");
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void should_reject_conflicting_static_and_instance_header_helpers() {
        var configurator = new AgenticServices.AgentConfigurator(
                ctx -> {}, null, null, null, type -> List.of(new CardHeadersHelper()));
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(
                        DeclarativeProtectedAgent.class, (ChatModel) null, configurator))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("@A2AAgentCardHeaders");
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    public interface AgentWithInvalidHeaderType {

        @A2AClientAgent(a2aServerUrl = "http://localhost:8080", outputKey = "response")
        String invoke(@V("request") String request);

        @A2AAgentCardHeaders
        static String cardHeaders() {
            return "not a map";
        }
    }

    @Test
    void should_reject_an_incompatible_return_type_before_fetching_the_card() {
        assertThatThrownBy(
                        () -> AgenticServices.createAgenticSystem(AgentWithInvalidHeaderType.class, (ChatModel) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must return java.util.Map");
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void should_reject_unresolved_header_parameters_before_fetching_the_card() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(AgentWithResolvedHeaders.class, (ChatModel) null))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("@A2AAgentCardHeaders")
                .hasMessageContaining("No SupplierParameterResolver");
        assertThat(server.getAllServeEvents()).isEmpty();
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
