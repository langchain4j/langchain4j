package dev.langchain4j.model.typesafe;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.router.DecisionModelChatModelRouter;
import dev.langchain4j.model.chat.router.RoutingChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * HTTP-contract coverage for the Nace recipe in the TypeSafe integration guide.
 * These tests use only a local server; they do not make live Nace requests.
 */
class TypeSafeDecisionModelNaceTest {

    private static final String PATH = "/v1/systemone";
    private static final String MODEL = "drex-max-v1.1";
    private static final String CLIENT = "langchain4j-typesafe-nace-example/1.0";

    // System One answers do not require a "type" field; Nace includes additional metadata.
    private static final String RESPONSE = """
            {
              "model": "drex-max-v1.1",
              "answers": {
                "refund": {"noul": 0.95, "confidence": 0.9},
                "team": {
                  "choice": "billing",
                  "probabilities": {"billing": 0.88, "support": 0.12},
                  "confidence": 0.81
                },
                "urgency": {
                  "score": 1.44,
                  "legend": {"0": "low", "1": "medium", "2": "high"},
                  "probabilities": {"0": 0.06, "1": 0.44, "2": 0.5},
                  "confidence": 0.78
                }
              },
              "usage": {"input_tokens": 318, "output_tokens": 0, "image_tokens": 0},
              "request_id": "nace-test-request",
              "evaluation_time_ms": 95
            }
            """;

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_use_nace_configuration_and_preserve_typed_answers(boolean async) throws Exception {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(RESPONSE)));
        AtomicReference<DecisionModelResponseContext> observed = new AtomicReference<>();
        DecisionModel model = model(new DecisionModelListener() {
            @Override
            public void onResponse(DecisionModelResponseContext context) {
                observed.set(context);
            }
        });
        DecisionRequest request = request();

        DecisionResponse response = async ? model.decideAsync(request).get(5, SECONDS) : model.decide(request);

        assertThat(response.modelName()).isEqualTo(MODEL);
        assertThat(response.yesNo("refund").probability()).isEqualTo(0.95);
        assertThat(response.choice("team").value()).isEqualTo("billing");
        assertThat(response.choice("team").probabilities())
                .containsExactlyInAnyOrderEntriesOf(Map.of("billing", 0.88, "support", 0.12));
        assertThat(response.choice("team").confidence()).isEqualTo(0.81);
        assertThat(response.scale("urgency").mean()).isEqualTo(1.44);
        assertThat(response.scale("urgency").probabilities()).containsExactly(0.06, 0.44, 0.5);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(318);
        assertThat(model.provider()).isEqualTo(ModelProvider.OTHER);
        assertThat(observed.get().modelProvider()).isEqualTo(ModelProvider.OTHER);
        assertThat(observed.get().decisionResponse()).isSameAs(response);

        server.verify(
                1,
                postRequestedFor(urlEqualTo(PATH))
                        .withHeader("Authorization", equalTo("Bearer nace-test-key"))
                        .withHeader("X-Nace-Client", equalTo(CLIENT))
                        .withHeader("User-Agent", equalTo(CLIENT))
                        .withRequestBody(matchingJsonPath("$.model", equalTo(MODEL)))
                        .withRequestBody(
                                matchingJsonPath("$.state", equalTo("I was charged twice. Please refund one charge.")))
                        .withRequestBody(matchingJsonPath("$.questions.refund.type", equalTo("noul")))
                        .withRequestBody(
                                matchingJsonPath("$.questions.team.criteria.billing", equalTo("Payments and refunds")))
                        .withRequestBody(matchingJsonPath("$.questions.urgency.criteria[2]", equalTo("high"))));
    }

    @Test
    void should_surface_authentication_error_without_retrying() {
        server.stubFor(post(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"type\":\"authentication_error\",\"message\":\"Invalid API key\"}}")));

        assertThatThrownBy(() -> model().decide(request())).isInstanceOf(AuthenticationException.class);

        server.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void should_reject_an_unknown_choice() {
        server.stubFor(post(urlEqualTo(PATH))
                .willReturn(okJson(RESPONSE.replace("\"choice\": \"billing\"", "\"choice\": \"unconfigured\""))));

        assertThatThrownBy(() -> model().decide(request())).isInstanceOf(InvalidDecisionResponseException.class);
    }

    @Test
    void should_reject_images_before_sending_even_when_the_selected_drex_model_supports_them() {
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(
                        List.of(TextContent.from("Is this an invoice?"), ImageContent.from("aGVsbG8=", "image/png")))
                .question("invoice", YesNoQuestion.from("Is this an invoice?"))
                .build();

        assertThatThrownBy(() -> model().decide(request)).isInstanceOf(UnsupportedFeatureException.class);
        server.verify(0, postRequestedFor(urlEqualTo(PATH)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_route_and_preserve_the_original_generation_request(boolean async) throws Exception {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(okJson("""
                {"model":"drex-max-v1.1","answers":{"route":{
                  "choice":"simple","probabilities":{"simple":0.9,"complex":0.1}
                }}}
                """)));
        AtomicReference<ChatRequest> selectedRequest = new AtomicReference<>();
        AtomicReference<ChatRequest> fallbackRequest = new AtomicReference<>();
        ChatModel routed = RoutingChatModel.builder()
                .route("simple", "Simple support requests", capturingModel(selectedRequest))
                .route("complex", "Complex troubleshooting", capturingModel(fallbackRequest))
                .router(new DecisionModelChatModelRouter(model()))
                .defaultRoute("complex")
                .build();
        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from("Answer using the supplied context."),
                        UserMessage.from("How do I reset my password?"))
                .temperature(0.2)
                .build();

        ChatResponse response = async ? routed.chatAsync(request).get(5, SECONDS) : routed.chat(request);

        assertThat(selectedRequest.get().messages()).isEqualTo(request.messages());
        assertThat(selectedRequest.get().parameters()).isEqualTo(request.parameters());
        assertThat(fallbackRequest).hasNullValue();
        assertThat(response.aiMessage().attributes()).containsEntry(RoutingChatModel.ROUTE_ATTRIBUTE, "simple");
        server.verify(
                1,
                postRequestedFor(urlEqualTo(PATH))
                        .withRequestBody(matchingJsonPath("$.questions.route.type", equalTo("choice"))));
    }

    @Test
    void should_use_the_configured_fallback_when_the_decision_service_fails() {
        server.stubFor(
                post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(529).withBody("{}")));
        AtomicReference<ChatRequest> simpleRequest = new AtomicReference<>();
        AtomicReference<ChatRequest> fallbackRequest = new AtomicReference<>();
        ChatModel routed = RoutingChatModel.builder()
                .route("simple", "Simple requests", capturingModel(simpleRequest))
                .route("complex", "Complex requests", capturingModel(fallbackRequest))
                .router(new DecisionModelChatModelRouter(model()))
                .defaultRoute("complex")
                .build();

        ChatResponse response = routed.chat(
                ChatRequest.builder().messages(UserMessage.from("Help")).build());

        assertThat(simpleRequest).hasNullValue();
        assertThat(fallbackRequest.get()).isNotNull();
        assertThat(response.aiMessage().attributes()).containsEntry(RoutingChatModel.ROUTE_ATTRIBUTE, "complex");
        server.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    private DecisionModel model(DecisionModelListener... listeners) {
        return new TypeSafeDecisionModel(TypeSafeDecisionModel.builder()
                .baseUrl(server.baseUrl())
                .apiKey("nace-test-key")
                .modelName(MODEL)
                .timeout(Duration.ofSeconds(5))
                .maxRetries(0)
                .customHeaders(Map.of("X-Nace-Client", CLIENT, "User-Agent", CLIENT))
                .listeners(listeners)) {
            @Override
            public ModelProvider provider() {
                return ModelProvider.OTHER;
            }
        };
    }

    private static DecisionRequest request() {
        return DecisionRequest.builder()
                .input("I was charged twice. Please refund one charge.")
                .question("refund", YesNoQuestion.from("Is the customer asking for a refund?"))
                .question(
                        "team",
                        ChoiceQuestion.builder()
                                .text("Which team should handle this?")
                                .option("billing", "Payments and refunds")
                                .option("support", "Technical problems")
                                .build())
                .question(
                        "urgency",
                        ScaleQuestion.builder()
                                .text("How urgent is this?")
                                .level("low")
                                .level("medium")
                                .level("high")
                                .build())
                .build();
    }

    private static ChatModel capturingModel(AtomicReference<ChatRequest> captured) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                captured.set(request);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("Answer"))
                        .build();
            }

            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest request) {
                return CompletableFuture.completedFuture(doChat(request));
            }
        };
    }
}
