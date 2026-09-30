package dev.langchain4j.model.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.decision.listener.DecisionModelErrorContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
            .input(Map.of("ticket", "Help! My payouts have been failing for 3 days."))
            .question(
                    "team",
                    ChoiceQuestion.builder()
                            .text("Which team should handle this?")
                            .option("billing", "Payments, invoicing, refunds")
                            .option("support", "Bugs. Not for invoices")
                            .build())
            .question(
                    "urgent",
                    YesNoQuestion.builder()
                            .text("Does this need attention today?")
                            .yesWhen("Money is not reaching the customer")
                            .build())
            .question(
                    "frustration",
                    ScaleQuestion.builder()
                            .text("How frustrated is the customer?")
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
                                                                        "support", "Bugs. Not for invoices")),
                                        "urgent",
                                                Map.of(
                                                        "type", "noul",
                                                        "instructions", "Does this need attention today?",
                                                        "criteria",
                                                                Map.of("true", "Money is not reaching the customer", "false", "")),
                                        "frustration",
                                                Map.of(
                                                        "type", "score",
                                                        "instructions", "How frustrated is the customer?",
                                                        "criteria", List.of("Calm", "Frustrated", "Angry")))));
    }

    @Test
    void should_omit_yes_no_criteria_when_not_set() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "jev-1.13.0", "answers": {"spam": {"type": "noul", "noul": 0.1}}}
                """));
        TypeSafeDecisionModel model = model(httpClient);

        // when
        model.decide(DecisionRequest.builder()
                .input("Hi, are we still meeting tomorrow?")
                .question("spam", YesNoQuestion.builder().text("Is this spam?").build())
                .build());

        // then
        Map<?, ?> body = Json.fromJson(httpClient.request().body(), Map.class);
        assertThat((Map<?, ?>) ((Map<?, ?>) body.get("questions")).get("spam"))
                .isEqualTo(Map.of("type", "noul", "instructions", "Is this spam?"));
    }

    @Test
    void should_send_names_as_descriptions_of_options_without_descriptions() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "jev-1.13.0", "answers": {"sentiment": {"type": "choice", "choice": "positive"}}}
                """));
        TypeSafeDecisionModel model = model(httpClient);

        // when
        model.decide(DecisionRequest.builder()
                .input("I love it!")
                .question("sentiment", ChoiceQuestion.of("What is the sentiment?", List.of("positive", "negative")))
                .build());

        // then
        assertThat(httpClient.request().body())
                .contains("\"criteria\":{\"positive\":\"positive\",\"negative\":\"negative\"}");
    }

    @Test
    void should_keep_null_values_in_state() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = model(httpClient);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("ticket", "Help! My payouts have been failing for 3 days.");
        state.put("assignee", null);

        // when
        model.decide(DecisionRequest.builder()
                .input(state)
                .questions(REQUEST.questions())
                .build());

        // then
        assertThat(httpClient.request().body())
                .contains("\"state\":{\"ticket\":\"Help! My payouts have been failing for 3 days.\",\"assignee\":null}");
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
                        .value("billing")
                        .probability("billing", 0.88)
                        .probability("support", 0.12)
                        .confidence(0.81)
                        .options(List.of("billing", "support"))
                        .build());
        assertThat(response.answers().get("urgent"))
                .isEqualTo(YesNoAnswer.builder().probability(0.95).build());
        assertThat(response.answers().get("frustration"))
                .isEqualTo(ScaleAnswer.builder()
                        .mean(1.44)
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
        ChoiceAnswer team = response.choice("team");
        assertThat(team.probabilities()).isEmpty();
        assertThat(team.confidence()).isNull();
        assertThat(response.yesNo("urgent").probability()).isEqualTo(1.0);
        assertThat(response.scale("frustration").probabilities())
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
        model.decide(REQUEST.toBuilder()
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
        record RankQuestion(String text) implements Question {}

        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = model(httpClient);
        DecisionRequest request = DecisionRequest.builder()
                .input("Refactor the parser")
                .question("next_step", new RankQuestion("Which step is best?"))
                .build();

        // when-then
        assertThatThrownBy(() -> model.decide(request))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("RankQuestion");
        assertThat(model.decideAsync(request))
                .failsWithin(Duration.ZERO)
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
        model.decide(REQUEST.toBuilder()
                .parameters(DecisionRequestParameters.builder()
                        .modelName("jev-1.13.0")
                        .build())
                .build());

        // then
        assertThat(Json.fromJson(httpClient.request().body(), Map.class)).containsEntry("model", "jev-1.13.0");
    }

    @Test
    void should_require_api_key_for_default_base_url() {

        assertThatThrownBy(() -> TypeSafeDecisionModel.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void should_require_api_key_when_default_base_url_is_set_explicitly() {

        assertThatThrownBy(() -> TypeSafeDecisionModel.builder()
                        .baseUrl("https://api.typesafe.ai/")
                        .apiKey(" ")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void should_not_send_blank_api_key_to_other_servers() {

        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:8000")
                .apiKey(" ")
                .modelName("local")
                .build();

        model.decide(REQUEST);

        assertThat(httpClient.request().headers()).doesNotContainKey("Authorization");
    }

    @Test
    void cancelling_decide_async_should_cancel_the_http_call() {

        // given
        CompletableFuture<SuccessfulHttpResponse> httpCall = new CompletableFuture<>();
        HttpClient httpClient = new HttpClient() {

            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
                return httpCall;
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("jev-latest")
                .build();

        // when
        model.decideAsync(REQUEST).cancel(true);

        // then
        assertThat(httpCall).isCancelled();
    }

    @Test
    void should_not_require_api_key_for_other_servers() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:8000")
                .modelName("local")
                .build();

        // when
        model.decide(REQUEST);

        // then
        assertThat(httpClient.request().headers()).doesNotContainKey("Authorization");
    }

    @Test
    void should_send_custom_headers() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("jev-latest")
                .customHeaders(Map.of("X-Tenant", "acme"))
                .build();

        // when
        model.decide(REQUEST);

        // then
        assertThat(httpClient.request().headers().get("X-Tenant")).containsExactly("acme");
    }

    @Test
    void should_notify_listeners_and_report_provider() {

        // given
        List<String> events = new ArrayList<>();
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(MockHttpClient.thatAlwaysResponds(ok(RESPONSE))))
                .apiKey("test-key")
                .modelName("jev-latest")
                .listeners(new DecisionModelListener() {

                    @Override
                    public void onRequest(DecisionModelRequestContext context) {
                        events.add("request:" + context.modelProvider() + ":" + context.decisionRequest().modelName());
                    }

                    @Override
                    public void onResponse(DecisionModelResponseContext context) {
                        events.add("response:" + context.decisionResponse().modelName());
                    }
                })
                .build();

        // when
        model.decide(REQUEST);

        // then
        assertThat(events).containsExactly("request:TYPESAFE:jev-latest", "response:jev-1.13.0");
        assertThat(model.provider()).isEqualTo(ModelProvider.TYPESAFE);
        assertThat(model.modelName()).isEqualTo("jev-latest");
    }

    @Test
    void should_retry_server_errors_twice_by_default() {

        // given
        FailingHttpClient httpClient = new FailingHttpClient(503);
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("jev-latest")
                .build();

        // when-then
        assertThatThrownBy(() -> model.decide(REQUEST)).isInstanceOf(InternalServerException.class);
        assertThat(httpClient.attempts).hasValue(3);
    }

    @Test
    void should_not_retry_client_errors() {

        // given
        FailingHttpClient httpClient = new FailingHttpClient(401);
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("wrong-key")
                .modelName("jev-latest")
                .build();

        // when-then
        assertThatThrownBy(() -> model.decide(REQUEST)).isInstanceOf(AuthenticationException.class);
        assertThat(httpClient.attempts).hasValue(1);
    }

    @Test
    void should_not_retry_responses_that_do_not_match_the_request() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(answers()));
        TypeSafeDecisionModel model = model(httpClient);

        // when-then
        assertThatThrownBy(() -> model.decide(REQUEST)).isInstanceOf(InvalidDecisionResponseException.class);
        assertThat(httpClient.requests()).hasSize(1);
    }

    @Test
    void should_map_http_errors_async_and_notify_listeners_with_the_cause() {

        // given
        List<Throwable> errors = new ArrayList<>();
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(new FailingHttpClient(401)))
                .apiKey("wrong-key")
                .modelName("jev-latest")
                .maxRetries(0)
                .listeners(new DecisionModelListener() {
                    @Override
                    public void onError(DecisionModelErrorContext context) {
                        errors.add(context.error());
                    }
                })
                .build();

        // when
        CompletableFuture<DecisionResponse> future = model.decideAsync(REQUEST);

        // then
        assertThat(future)
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(AuthenticationException.class);
        assertThat(errors).singleElement().isInstanceOf(AuthenticationException.class);
    }

    @Test
    void should_reject_async_responses_that_do_not_match_the_request_and_notify_listeners_with_the_cause() {

        // given
        List<Throwable> errors = new ArrayList<>();
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(MockHttpClient.thatAlwaysResponds(ok(answers()))))
                .apiKey("test-key")
                .modelName("jev-latest")
                .listeners(new DecisionModelListener() {
                    @Override
                    public void onError(DecisionModelErrorContext context) {
                        errors.add(context.error());
                    }
                })
                .build();

        // when
        CompletableFuture<DecisionResponse> future = model.decideAsync(REQUEST);

        // then
        assertThat(future)
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(InvalidDecisionResponseException.class);
        assertThat(errors).singleElement().isInstanceOf(InvalidDecisionResponseException.class);
    }

    @Test
    void should_fill_missing_level_probabilities_with_zero() {

        // given
        TypeSafeDecisionModel model = model(MockHttpClient.thatAlwaysResponds(ok(answers(
                "\"urgent\": {\"type\": \"noul\", \"noul\": 0.9}",
                "\"team\": {\"type\": \"choice\", \"choice\": \"billing\"}",
                "\"frustration\": {\"type\": \"score\", \"score\": 0.6,"
                        + " \"probabilities\": {\"0\": 0.7, \"2\": 0.3}}"))));

        // when
        DecisionResponse response = model.decide(REQUEST);

        // then
        assertThat(response.scale("frustration").probabilities()).containsExactly(0.7, 0.0, 0.3);
    }

    @Test
    void should_accept_answers_without_type_and_clamp_rounding_errors_below_zero() {

        // given
        TypeSafeDecisionModel model = model(MockHttpClient.thatAlwaysResponds(ok(answers(
                "\"urgent\": {\"noul\": -0.0000000001}",
                "\"team\": {\"choice\": \"billing\"}",
                "\"frustration\": {\"score\": -0.0000000001}"))));

        // when
        DecisionResponse response = model.decide(REQUEST);

        // then
        assertThat(response.yesNo("urgent").probability()).isEqualTo(0.0);
        assertThat(response.choice("team").value()).isEqualTo("billing");
        assertThat(response.scale("frustration").mean()).isEqualTo(0.0);
    }

    @Test
    void should_call_custom_headers_supplier_for_each_request() {

        // given
        AtomicInteger calls = new AtomicInteger();
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("jev-latest")
                .customHeaders(() -> Map.of("X-Token", "token-" + calls.incrementAndGet()))
                .build();

        // when
        model.decide(REQUEST);
        model.decide(REQUEST);

        // then
        assertThat(httpClient.requests())
                .extracting(request -> request.headers().get("X-Token"))
                .containsExactly(List.of("token-1"), List.of("token-2"));
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void should_reject_responses_that_do_not_match_the_request(String body, String expectedMessage) {

        TypeSafeDecisionModel model = model(MockHttpClient.thatAlwaysResponds(ok(body)));

        assertThatThrownBy(() -> model.decide(REQUEST))
                .isInstanceOf(InvalidDecisionResponseException.class)
                .hasMessageContaining(expectedMessage);
    }

    static List<Arguments> invalidResponses() {
        String urgent = "\"urgent\": {\"type\": \"noul\", \"noul\": 0.9}";
        String frustration = "\"frustration\": {\"type\": \"score\", \"score\": 1.0}";
        String team = "\"team\": {\"type\": \"choice\", \"choice\": \"billing\"}";
        return List.of(
                Arguments.of("{\"model\": \"jev\"}", "no answer to question"),
                Arguments.of(answers(urgent, frustration), "no answer to question 'team'"),
                Arguments.of(
                        answers(urgent, frustration, "\"team\": {\"type\": \"choice\", \"choice\": \"sales\"}"),
                        "'sales', which is not one of the options"),
                Arguments.of(
                        answers(urgent, frustration, "\"team\": {\"type\": \"noul\", \"noul\": 0.5}"),
                        "has type 'noul' instead of 'choice'"),
                Arguments.of(
                        answers(frustration, team, "\"urgent\": {\"type\": \"noul\"}"), "'urgent' has no 'noul'"),
                Arguments.of(
                        answers(frustration, team, "\"urgent\": {\"type\": \"noul\", \"noul\": 1.3}"),
                        "invalid noul: 1.3"),
                Arguments.of(
                        answers(
                                urgent,
                                frustration,
                                "\"team\": {\"type\": \"choice\", \"choice\": \"billing\","
                                        + " \"probabilities\": {\"billing\": 0.9, \"sales\": 0.1}}"),
                        "probability for 'sales'"),
                Arguments.of(
                        answers(
                                urgent,
                                team,
                                "\"frustration\": {\"type\": \"score\", \"score\": 1.0,"
                                        + " \"probabilities\": {\"Calm\": 1.0}}"),
                        "level 'Calm', but the levels are 0 to 2"),
                Arguments.of(
                        answers(urgent, team, "\"frustration\": {\"type\": \"score\", \"score\": 7.0}"),
                        "invalid score: 7.0, but the levels are 0 to 2"),
                Arguments.of(
                        answers(urgent, team, "\"frustration\": {\"type\": \"score\", \"score\": -1.0}"),
                        "invalid score: -1.0, but the levels are 0 to 2"),
                Arguments.of(
                        answers(
                                urgent,
                                team,
                                "\"frustration\": {\"type\": \"score\", \"score\": 1.0,"
                                        + " \"probabilities\": {\"01\": 1.0}}"),
                        "level '01', but the levels are 0 to 2"),
                Arguments.of(
                        answers(
                                urgent,
                                frustration,
                                "\"team\": {\"type\": \"choice\", \"choice\": \"billing\","
                                        + " \"probabilities\": {\"billing\": 1.5}}"),
                        "invalid probability of 'billing': 1.5"),
                Arguments.of(
                        answers(
                                urgent,
                                frustration,
                                "\"team\": {\"type\": \"choice\", \"choice\": \"billing\", \"confidence\": 1.5}"),
                        "invalid confidence: 1.5"));
    }

    private static String answers(String... answers) {
        return "{\"model\": \"jev-1.13.0\", \"answers\": {" + String.join(", ", answers) + "}}";
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

    private static class FailingHttpClient implements HttpClient {

        private final int statusCode;
        private final AtomicInteger attempts = new AtomicInteger();

        FailingHttpClient(int statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            attempts.incrementAndGet();
            throw new HttpException(statusCode, "{\"detail\": \"error\"}");
        }

        @Override
        public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
            attempts.incrementAndGet();
            return CompletableFuture.failedFuture(new HttpException(statusCode, "{\"detail\": \"error\"}"));
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
