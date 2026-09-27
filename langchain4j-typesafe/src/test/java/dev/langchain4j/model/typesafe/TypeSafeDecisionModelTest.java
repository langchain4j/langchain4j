package dev.langchain4j.model.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.NoulQuestion;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScoreQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.NoulAnswer;
import dev.langchain4j.model.decision.response.ScoreAnswer;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class TypeSafeDecisionModelTest {

    private static final String RESPONSE =
            """
            {
              "model": "jev-1.13.0",
              "answers": {
                "team": {
                  "type": "choice",
                  "choice": "billing",
                  "probabilities": {"billing": 0.88, "support": 0.12},
                  "confidence": 0.81
                },
                "urgent": {"type": "noul", "noul": 0.95},
                "frustration": {
                  "type": "score",
                  "score": 1.44,
                  "legend": {"0": "Calm", "1": "Frustrated", "2": "Angry"},
                  "probabilities": {"0": 0.06, "1": 0.44, "2": 0.5},
                  "confidence": 0.78
                }
              },
              "usage": {"input_tokens": 318, "output_tokens": 34},
              "latency_ms": 95
            }
            """;

    private static final DecisionRequest REQUEST = DecisionRequest.builder()
            .state(Map.of("ticket", "Help! My payouts have been failing for 3 days."))
            .question(
                    "team",
                    ChoiceQuestion.builder()
                            .instructions("Which team should handle this?")
                            .option("billing", "Payments, invoicing, refunds")
                            .option("support", Map.of("what", "Bugs", "not_for", "Invoices"))
                            .build())
            .question(
                    "urgent",
                    NoulQuestion.builder()
                            .instructions("Does this need attention today?")
                            .whenTrue("Money is not reaching the customer")
                            .build())
            .question(
                    "frustration",
                    ScoreQuestion.builder()
                            .instructions("How frustrated is the customer?")
                            .level("Calm")
                            .level("Frustrated")
                            .level("Angry")
                            .build())
            .build();

    @Test
    void should_send_request_in_system_one_format() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = model(httpClient);

        // when
        model.decide(REQUEST);

        // then
        HttpRequest request = httpClient.request();
        assertThat(request.url()).isEqualTo("https://api.typesafe.ai/v1/systemone");
        assertThat(request.headers().get("Authorization")).containsExactly("Bearer test-key");

        assertThat(Json.fromJson(request.body(), Map.class))
                .isEqualTo(Map.of(
                        "model", "jev-latest",
                        "state", Map.of("ticket", "Help! My payouts have been failing for 3 days."),
                        "questions",
                                Map.of(
                                        "team",
                                                Map.of(
                                                        "type", "choice",
                                                        "instructions", "Which team should handle this?",
                                                        "criteria",
                                                                Map.of(
                                                                        "billing", "Payments, invoicing, refunds",
                                                                        "support",
                                                                                Map.of(
                                                                                        "what", "Bugs",
                                                                                        "not_for", "Invoices"))),
                                        "urgent",
                                                Map.of(
                                                        "type", "noul",
                                                        "instructions", "Does this need attention today?",
                                                        "criteria",
                                                                Map.of("true", "Money is not reaching the customer")),
                                        "frustration",
                                                Map.of(
                                                        "type", "score",
                                                        "instructions", "How frustrated is the customer?",
                                                        "criteria", List.of("Calm", "Frustrated", "Angry")))));
    }

    @Test
    void should_omit_noul_criteria_when_not_set() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "jev-1.13.0", "answers": {"spam": {"type": "noul", "noul": 0.1}}}
                """));
        TypeSafeDecisionModel model = model(httpClient);

        // when
        model.decide(DecisionRequest.builder()
                .state("Hi, are we still meeting tomorrow?")
                .question("spam", NoulQuestion.builder().instructions("Is this spam?").build())
                .build());

        // then
        Map<?, ?> body = Json.fromJson(httpClient.request().body(), Map.class);
        assertThat((Map<?, ?>) ((Map<?, ?>) body.get("questions")).get("spam"))
                .isEqualTo(Map.of("type", "noul", "instructions", "Is this spam?"));
    }

    @Test
    void should_map_response() {

        // given
        TypeSafeDecisionModel model = model(MockHttpClient.thatAlwaysResponds(ok(RESPONSE)));

        // when
        DecisionResponse response = model.decide(REQUEST);

        // then
        assertThat(response.answers().keySet()).containsExactly("team", "urgent", "frustration");
        assertThat(response.answers().get("team"))
                .isEqualTo(ChoiceAnswer.builder()
                        .choice("billing")
                        .probability("billing", 0.88)
                        .probability("support", 0.12)
                        .confidence(0.81)
                        .build());
        assertThat(response.answers().get("urgent"))
                .isEqualTo(NoulAnswer.builder().probability(0.95).build());
        assertThat(response.answers().get("frustration"))
                .isEqualTo(ScoreAnswer.builder()
                        .score(1.44)
                        .probabilities(List.of(0.06, 0.44, 0.5))
                        .confidence(0.78)
                        .build());
        assertThat(response.modelName()).isEqualTo("jev-1.13.0");
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(318, 34));
    }

    @Test
    void should_accept_answers_without_probabilities_and_confidence() {

        // given
        TypeSafeDecisionModel model = model(MockHttpClient.thatAlwaysResponds(ok(
                """
                {
                  "model": "local",
                  "answers": {
                    "team": {"type": "choice", "choice": "support"},
                    "urgent": {"type": "noul", "noul": 1.0000000002},
                    "frustration": {"type": "score", "score": 0.2}
                  }
                }
                """)));

        // when
        DecisionResponse response = model.decide(REQUEST);

        // then
        ChoiceAnswer team = (ChoiceAnswer) response.answers().get("team");
        assertThat(team.probabilities()).isEmpty();
        assertThat(team.confidence()).isNull();
        assertThat(((NoulAnswer) response.answers().get("urgent")).probability()).isEqualTo(1.0);
        assertThat(((ScoreAnswer) response.answers().get("frustration")).probabilities())
                .isEmpty();
        assertThat(response.tokenUsage()).isNull();
    }

    @Test
    void should_use_custom_base_url_and_model_name() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:8000")
                .apiKey("test-key")
                .modelName("kev-latest")
                .build();

        // when
        model.decide(REQUEST);

        // then
        assertThat(httpClient.request().url()).isEqualTo("http://localhost:8000/v1/systemone");
        assertThat(Json.fromJson(httpClient.request().body(), Map.class)).containsEntry("model", "kev-latest");
    }

    @Test
    void request_model_name_should_override_default() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = model(httpClient);

        // when
        model.decide(DecisionRequest.builder()
                .state(REQUEST.state())
                .questions(REQUEST.questions())
                .parameters(DecisionRequestParameters.builder()
                        .modelName("jev-1.13.0")
                        .build())
                .build());

        // then
        assertThat(Json.fromJson(httpClient.request().body(), Map.class)).containsEntry("model", "jev-1.13.0");
    }

    @Test
    void should_decide_async() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = model(httpClient);

        // when
        DecisionResponse response = model.decideAsync(REQUEST).get();

        // then
        assertThat(response.answers()).containsOnlyKeys("team", "urgent", "frustration");
        assertThat(response.modelName()).isEqualTo("jev-1.13.0");
        assertThat(httpClient.request().url()).isEqualTo("https://api.typesafe.ai/v1/systemone");
    }

    @Test
    void should_reject_unsupported_question_type_before_sending_request() {

        // given
        record RankQuestion(String instructions) implements Question {}

        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = model(httpClient);
        DecisionRequest request = DecisionRequest.builder()
                .state("Refactor the parser")
                .question("next_step", new RankQuestion("Which step is best?"))
                .build();

        // when-then
        assertThatThrownBy(() -> model.decide(request))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("RankQuestion");
        assertThat(model.decideAsync(request))
                .failsWithin(java.time.Duration.ZERO)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(UnsupportedFeatureException.class);
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_map_http_errors() {

        // given
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(new FailingHttpClient(401)))
                .apiKey("wrong-key")
                .modelName("jev-latest")
                .maxRetries(0)
                .build();

        // when-then
        assertThatThrownBy(() -> model.decide(REQUEST)).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void should_require_model_name() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .build();

        // when-then
        assertThatThrownBy(() -> model.decide(REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_use_model_name_from_request_when_not_set_on_model() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .build();

        // when
        model.decide(DecisionRequest.builder()
                .state(REQUEST.state())
                .questions(REQUEST.questions())
                .parameters(DecisionRequestParameters.builder()
                        .modelName("jev-1.13.0")
                        .build())
                .build());

        // then
        assertThat(Json.fromJson(httpClient.request().body(), Map.class)).containsEntry("model", "jev-1.13.0");
    }

    @Test
    void should_require_api_key() {

        assertThatThrownBy(() -> TypeSafeDecisionModel.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    private static TypeSafeDecisionModel model(MockHttpClient httpClient) {
        return TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("jev-latest")
                .build();
    }

    private static SuccessfulHttpResponse ok(String body) {
        return SuccessfulHttpResponse.builder().statusCode(200).body(body).build();
    }

    private record FailingHttpClient(int statusCode) implements HttpClient {

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            throw new HttpException(statusCode, "{\"detail\": \"Invalid API key\"}");
        }

        @Override
        public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
            return CompletableFuture.failedFuture(new HttpException(statusCode, "{\"detail\": \"Invalid API key\"}"));
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
