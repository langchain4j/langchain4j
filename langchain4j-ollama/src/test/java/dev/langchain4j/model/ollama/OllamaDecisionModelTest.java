package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.listener.DecisionModelErrorContext;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionInputType;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DefaultDecisionRequestParameters;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.output.TokenUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OllamaDecisionModelTest {

    private static final String RESPONSE =
            """
            {
              "model": "nimble",
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
              "usage": {"input_tokens": 318, "output_tokens": 4},
              "prompt_eval_cached_count": 62
            }
            """;

    private static final DecisionRequest REQUEST = DecisionRequest.builder()
            .inputFields(Map.of("ticket", "Help! My payouts have been failing for 3 days."))
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
                            .noWhen("Only questions or feature requests")
                            .build())
            .question(
                    "frustration",
                    ScaleQuestion.builder()
                            .text("How frustrated is the customer?")
                            .level("Calm")
                            .level("Frustrated")
                            .level("Angry", "Strong language or threatening to leave")
                            .build())
            .build();

    @Test
    void should_reject_parameters_of_another_decision_model_without_calling_the_api() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        OllamaDecisionModel model = OllamaDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("nimble")
                .build();
        DecisionRequest request =
                REQUEST.toBuilder().parameters(new OtherModelParameters()).build();

        // when-then
        assertThatThrownBy(() -> model.decide(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OtherModelParameters")
                .hasMessageContaining("OllamaDecisionRequestParameters");
        assertThat(model.decideAsync(request))
                .failsWithin(Duration.ZERO)
                .withThrowableOfType(ExecutionException.class)
                .withCauseExactlyInstanceOf(IllegalArgumentException.class);
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_send_custom_parameters_set_on_builder() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        OllamaDecisionModel model = OllamaDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("nimble")
                .customParameters(Map.of(
                        "new_parameter", "value",
                        "model", "custom-model",
                        "state", "custom state",
                        "questions", List.of("custom question")))
                .build();

        // when
        model.decide(REQUEST);

        // then
        String body = httpClient.requests().get(0).body();
        assertThat(Json.fromJson(body, Map.class))
                .containsEntry("new_parameter", "value")
                .containsEntry("model", "custom-model")
                .containsEntry("state", "custom state")
                .containsEntry("questions", List.of("custom question"));
        assertThat(body).containsOnlyOnce("\"model\"");
        assertThat(body).containsOnlyOnce("\"state\"");
        assertThat(body).containsOnlyOnce("\"questions\"");
    }

    @Test
    void should_replace_builder_custom_parameters_per_request() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        OllamaDecisionModel model = OllamaDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("nimble")
                .customParameters(Map.of("a", "1", "b", "2"))
                .build();

        // when
        model.decide(REQUEST.toBuilder()
                .parameters(OllamaDecisionRequestParameters.builder()
                        .customParameters(Map.of("b", "3"))
                        .build())
                .build());
        model.decide(REQUEST.toBuilder()
                .parameters(OllamaDecisionRequestParameters.builder().customParameters(Map.of()).build())
                .build());
        model.decide(REQUEST.toBuilder()
                .parameters(DecisionRequestParameters.builder().modelName("other-model").build())
                .build());

        // then
        assertThat(Json.fromJson(httpClient.requests().get(0).body(), Map.class))
                .containsEntry("b", "3")
                .doesNotContainKey("a");
        assertThat(Json.fromJson(httpClient.requests().get(1).body(), Map.class))
                .containsEntry("a", "1")
                .containsEntry("b", "2");
        assertThat(Json.fromJson(httpClient.requests().get(2).body(), Map.class))
                .containsEntry("a", "1")
                .containsEntry("b", "2")
                .containsEntry("model", "other-model");
    }

    @Test
    void should_send_request_in_system_one_format() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));

        // when
        model(httpClient).decide(REQUEST);

        // then
        HttpRequest request = httpClient.request();
        assertThat(request.url()).isEqualTo("http://localhost:11434/v1/systemone");
        assertThat(request.headers()).doesNotContainKey("Authorization");
        assertThat(Json.fromJson(request.body(), Map.class))
                .isEqualTo(Map.of(
                        "model", "nimble",
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
                                                                Map.of(
                                                                        "true", "Money is not reaching the customer",
                                                                        "false", "Only questions or feature requests")),
                                        "frustration",
                                                Map.of(
                                                        "type", "score",
                                                        "instructions", "How frustrated is the customer?",
                                                        "criteria",
                                                                List.of(
                                                                        "Calm",
                                                                        "Frustrated",
                                                                        "Angry: Strong language or threatening to leave")))));
    }

    @Test
    void should_send_keep_alive() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        OllamaDecisionModel model = OllamaDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("nimble")
                .keepAlive(-1)
                .build();

        // when
        model.decide(REQUEST);

        // then
        assertThat(Json.fromJson(httpClient.request().body(), Map.class)).containsEntry("keep_alive", -1);
    }

    @Test
    void should_map_response() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));

        // when
        DecisionResponse response = model(httpClient).decide(REQUEST);

        // then
        assertThat(response.modelName()).isEqualTo("nimble");
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(318, 4));
        assertThat(response.yesNo("urgent").probability()).isEqualTo(0.95);

        ChoiceAnswer team = response.choice("team");
        assertThat(team.value()).isEqualTo("billing");
        assertThat(team.probabilities()).containsExactly(Map.entry("billing", 0.88), Map.entry("support", 0.12));
        assertThat(team.confidence()).isEqualTo(0.81);

        ScaleAnswer frustration = response.scale("frustration");
        assertThat(frustration.mean()).isEqualTo(1.44);
        assertThat(frustration.probabilities()).containsExactly(0.06, 0.44, 0.5);
        assertThat(frustration.confidence()).isEqualTo(0.78);
    }

    @Test
    void should_map_response_asynchronously() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));

        // when
        DecisionResponse response = model(httpClient).decideAsync(REQUEST).get();

        // then
        assertThat(response.choice("team").value()).isEqualTo("billing");
        assertThat(httpClient.request().url()).isEqualTo("http://localhost:11434/v1/systemone");
    }

    @Test
    void should_append_single_yes_no_criterion_to_instructions() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "nimble", "answers": {"urgent": {"type": "noul", "noul": 0.1}, "spam": {"type": "noul", "noul": 0.2}}}
                """));

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .input("Nobody can log in since this morning.")
                        .question(
                                "urgent",
                                YesNoQuestion.builder()
                                        .text("Is this ticket urgent?")
                                        .noWhen("Login problems are never urgent")
                                        .build())
                        .question(
                                "spam",
                                YesNoQuestion.builder()
                                        .text("Is this spam?")
                                        .yesWhen("It advertises something")
                                        .build())
                        .build());

        // then
        Map<?, ?> questions = (Map<?, ?>) Json.fromJson(httpClient.request().body(), Map.class).get("questions");
        assertThat(questions.get("urgent"))
                .isEqualTo(Map.of(
                        "type", "noul",
                        "instructions", "Is this ticket urgent?\nAnswer no when: Login problems are never urgent"));
        assertThat(questions.get("spam"))
                .isEqualTo(Map.of(
                        "type", "noul", "instructions", "Is this spam?\nAnswer yes when: It advertises something"));
    }

    @Test
    void should_send_images_separately_and_refer_to_them_in_the_state() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "clef", "answers": {"damaged": {"type": "noul", "noul": 0.9}}}
                """));

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .inputContents(List.of(
                                TextContent.from("The customer sent two photos of the delivered phone."),
                                ImageContent.from("Zmlyc3Q=", "image/png"),
                                ImageContent.from("c2Vjb25k", "image/jpeg")))
                        .question("damaged", YesNoQuestion.builder().text("Is the phone damaged?").build())
                        .build());

        // then
        Map<?, ?> body = Json.fromJson(httpClient.request().body(), Map.class);
        assertThat(body.get("state"))
                .isEqualTo("The customer sent two photos of the delivered phone.\n\n[image 1]\n\n[image 2]");
        assertThat(body.get("images")).isEqualTo(List.of("Zmlyc3Q=", "c2Vjb25k"));
    }

    @Test
    void should_send_named_images_of_map_input_separately() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "clef", "answers": {"matches": {"type": "noul", "noul": 0.9}}}
                """));

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .inputFields(Map.of(
                                "comment", "Broken screen on arrival",
                                "photo", ImageContent.from("cGhvdG8=", "image/webp")))
                        .question(
                                "matches",
                                YesNoQuestion.builder()
                                        .text("Does the photo show what the comment describes?")
                                        .build())
                        .build());

        // then
        Map<?, ?> body = Json.fromJson(httpClient.request().body(), Map.class);
        assertThat(body.get("state")).isEqualTo(Map.of("comment", "Broken screen on arrival", "photo", "[image 1]"));
        assertThat(body.get("images")).isEqualTo(List.of("cGhvdG8="));
    }

    @Test
    void should_not_send_images_for_text_input() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "nimble", "answers": {"spam": {"type": "noul", "noul": 0.1}}}
                """));

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .input("Hi, are we still meeting tomorrow?")
                        .question("spam", YesNoQuestion.builder().text("Is this spam?").build())
                        .build());

        // then
        Map<?, ?> body = Json.fromJson(httpClient.request().body(), Map.class);
        assertThat(body.containsKey("images")).isFalse();
        assertThat(body.get("state")).isEqualTo("Hi, are we still meeting tomorrow?");
    }

    @Test
    void should_reject_custom_input_without_calling_the_api() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        DecisionRequest request = DecisionRequest.builder()
                .input(() -> DecisionInputType.TEXT)
                .question("urgent", YesNoQuestion.from("Does this need attention today?"))
                .build();

        // when-then
        assertThatThrownBy(() -> model(httpClient).decide(request))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageStartingWith("Ollama decision models do not support ");
        assertThat(model(httpClient).decideAsync(request))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(UnsupportedFeatureException.class);
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_unsupported_messages_of_conversation_without_calling_ollama() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));

        // when-then
        assertThatThrownBy(() -> model(httpClient)
                        .decide(DecisionRequest.builder()
                                .inputMessages(UserMessage.from("Hi"), CustomMessage.from(Map.of("key", "value")))
                                .questions(REQUEST.questions())
                                .build()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("The Ollama decision model does not support CustomMessage in a conversation. Supported messages: "
                        + "SystemMessage, UserMessage, AiMessage and ToolExecutionResultMessage");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_send_conversation_as_transcript_in_state() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        ImageContent photo = ImageContent.from("iVBORw0KGgo=", "image/png");

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .inputMessages(
                                SystemMessage.from("You are a support agent."),
                                UserMessage.from(TextContent.from("It arrived like this:"), photo),
                                AiMessage.from("I am sorry to hear that."))
                        .questions(REQUEST.questions())
                        .build());

        // then
        Map<?, ?> body = Json.fromJson(httpClient.request().body(), Map.class);
        assertThat(body.get("state"))
                .isEqualTo(Map.of(
                        "messages",
                        List.of(
                                Map.of("role", "system", "text", "You are a support agent."),
                                Map.of("role", "user", "text", "It arrived like this:\n[image 1]"),
                                Map.of("role", "assistant", "text", "I am sorry to hear that."))));
        assertThat(body.get("images")).isEqualTo(List.of("iVBORw0KGgo="));
    }

    @Test
    void should_reject_audio_input_without_calling_ollama() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));

        // when-then
        assertThatThrownBy(() -> model(httpClient)
                        .decide(DecisionRequest.builder()
                                .inputContents(List.of(AudioContent.from("YXVkaW8=", "audio/wav")))
                                .question("spam", YesNoQuestion.builder().text("Is this spam?").build())
                                .build()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("Ollama decision models support only text and image input, but the input contains AUDIO");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_use_model_name_from_request() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "tev1", "answers": {"spam": {"type": "noul", "noul": 0.1}}}
                """));

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .input("Hello")
                        .question("spam", YesNoQuestion.builder().text("Is this spam?").build())
                        .parameters(DecisionRequestParameters.builder()
                                .modelName("tev1")
                                .build())
                        .build());

        // then
        assertThat(Json.fromJson(httpClient.request().body(), Map.class).get("model")).isEqualTo("tev1");
    }

    @Test
    void should_fail_without_model_name() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        OllamaDecisionModel model = OllamaDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .build();

        // when-then
        assertThatThrownBy(() -> model.decide(REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OllamaDecisionModel.builder().modelName(...)");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_answer_of_wrong_type() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "nimble", "answers": {"spam": {"type": "score", "score": 0.5}}}
                """));

        // when-then
        assertThatThrownBy(() -> model(httpClient)
                        .decide(DecisionRequest.builder()
                                .input("Hello")
                                .question("spam", YesNoQuestion.builder().text("Is this spam?").build())
                                .build()))
                .isInstanceOf(InvalidDecisionResponseException.class)
                .hasMessage("The answer to question 'spam' has type 'score' instead of 'noul'");
    }

    @Test
    void should_number_images_across_all_values_of_map_input() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "clef", "answers": {"same": {"type": "noul", "noul": 0.9}}}
                """));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("comment", "Is this the same phone?");
        input.put("listing", ImageContent.from("bGlzdGluZw==", "image/png"));
        input.put("delivered", List.of(
                ImageContent.from("ZnJvbnQ=", "image/jpeg"),
                TextContent.from("and the back:"),
                ImageContent.from("YmFjaw==", "image/jpeg")));
        input.put("price", 499);
        input.put("seller", null);

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .inputFields(input)
                        .question("same", YesNoQuestion.from("Do the photos show the same phone?"))
                        .build());

        // then
        Map<?, ?> body = Json.fromJson(httpClient.request().body(), Map.class);
        Map<String, Object> expectedState = new LinkedHashMap<>();
        expectedState.put("comment", "Is this the same phone?");
        expectedState.put("listing", "[image 1]");
        expectedState.put("delivered", "[image 2]\n\nand the back:\n\n[image 3]");
        expectedState.put("price", 499);
        expectedState.put("seller", null);
        assertThat(body.get("state")).isEqualTo(expectedState);
        assertThat(body.get("images")).isEqualTo(List.of("bGlzdGluZw==", "ZnJvbnQ=", "YmFjaw=="));
    }

    @Test
    void should_read_images_from_file_urls(@TempDir Path tempDir) throws Exception {

        // given
        Path image = tempDir.resolve("photo.png");
        Files.write(image, new byte[] {1, 2, 3});
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "clef", "answers": {"damaged": {"type": "noul", "noul": 0.9}}}
                """));

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .inputContents(List.of(ImageContent.from(image.toUri())))
                        .question("damaged", YesNoQuestion.from("Is the phone damaged?"))
                        .build());

        // then
        Map<?, ?> body = Json.fromJson(httpClient.request().body(), Map.class);
        assertThat(body.get("state")).isEqualTo("[image 1]");
        assertThat(body.get("images"))
                .isEqualTo(List.of(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})));
    }

    @Test
    void should_send_levels_without_descriptions_as_their_labels() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "nimble", "answers": {"severity": {"type": "score", "score": 1.0}}}
                """));

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .input("The whole product is down.")
                        .question(
                                "severity",
                                ScaleQuestion.builder()
                                        .text("How severe is the incident?")
                                        .level("Minor")
                                        .level("Major")
                                        .level("Critical")
                                        .build())
                        .build());

        // then
        Map<?, ?> questions = (Map<?, ?>) Json.fromJson(httpClient.request().body(), Map.class).get("questions");
        assertThat(((Map<?, ?>) questions.get("severity")).get("criteria"))
                .isEqualTo(List.of("Minor", "Major", "Critical"));
    }

    @Test
    void should_reject_unsupported_question_type_before_sending_request() {

        // given
        record RankQuestion(String text) implements Question {}

        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        OllamaDecisionModel model = model(httpClient);
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
    void should_map_unknown_model_to_model_not_found_exception() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysFails(
                new HttpException(404, "{\"error\":\"model \\\"nope\\\" not found, try pulling it first\"}"));

        // when-then
        assertThatThrownBy(() -> model(httpClient).decide(REQUEST)).isInstanceOf(ModelNotFoundException.class);
        assertThat(httpClient.requests()).hasSize(1);
    }

    @Test
    void should_retry_server_errors_twice_by_default() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysFails(new HttpException(503, "{\"error\":\"busy\"}"));

        // when-then
        assertThatThrownBy(() -> model(httpClient).decide(REQUEST)).isInstanceOf(InternalServerException.class);
        assertThat(httpClient.requests()).hasSize(3);
    }

    @Test
    void should_not_retry_client_errors() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysFails(new HttpException(
                400, "{\"error\":\"question \\\"q\\\": score criteria must be an array of descriptions\"}"));

        // when-then
        assertThatThrownBy(() -> model(httpClient).decide(REQUEST))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("score criteria must be an array of descriptions");
        assertThat(httpClient.requests()).hasSize(1);
    }

    @Test
    void should_not_retry_responses_that_do_not_match_the_request() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(answers()));

        // when-then
        assertThatThrownBy(() -> model(httpClient).decide(REQUEST))
                .isInstanceOf(InvalidDecisionResponseException.class);
        assertThat(httpClient.requests()).hasSize(1);
    }

    @Test
    void should_map_http_errors_async_and_notify_listeners_with_the_cause() {

        // given
        List<Throwable> errors = new ArrayList<>();
        OllamaDecisionModel model = OllamaDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(
                        MockHttpClient.thatAlwaysFails(new HttpException(404, "{\"error\":\"not found\"}"))))
                .baseUrl("http://localhost:11434")
                .modelName("nimble")
                .listeners(new DecisionModelListener() {
                    @Override
                    public void onError(DecisionModelErrorContext context) {
                        errors.add(context.error());
                    }
                })
                .build();

        // when-then
        assertThat(model.decideAsync(REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(ModelNotFoundException.class);
        assertThat(errors).singleElement().isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void should_reject_async_responses_that_do_not_match_the_request() {

        // when-then
        assertThat(model(MockHttpClient.thatAlwaysResponds(ok(answers()))).decideAsync(REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(InvalidDecisionResponseException.class);
    }

    @Test
    void should_notify_listeners_and_report_provider() {

        // given
        List<String> events = new ArrayList<>();
        OllamaDecisionModel model = OllamaDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(MockHttpClient.thatAlwaysResponds(ok(RESPONSE))))
                .baseUrl("http://localhost:11434")
                .modelName("nimble")
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
        assertThat(events).containsExactly("request:OLLAMA:nimble", "response:nimble");
        assertThat(model.provider()).isEqualTo(ModelProvider.OLLAMA);
        assertThat(model.modelName()).isEqualTo("nimble");
    }

    @Test
    void should_call_custom_headers_supplier_for_each_request() {

        // given
        AtomicInteger calls = new AtomicInteger();
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        OllamaDecisionModel model = OllamaDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("nimble")
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

    @Test
    void should_accept_answers_without_probabilities_and_confidence() {

        // given
        OllamaDecisionModel model = model(MockHttpClient.thatAlwaysResponds(ok(answers(
                "\"team\": {\"type\": \"choice\", \"choice\": \"support\"}",
                "\"urgent\": {\"type\": \"noul\", \"noul\": 1.0000000002}",
                "\"frustration\": {\"type\": \"score\", \"score\": 0.2}"))));

        // when
        DecisionResponse response = model.decide(REQUEST);

        // then
        assertThat(response.choice("team").probabilities()).isEmpty();
        assertThat(response.choice("team").confidence()).isNull();
        assertThat(response.yesNo("urgent").probability()).isEqualTo(1.0);
        assertThat(response.scale("frustration").probabilities()).isEmpty();
        assertThat(response.tokenUsage()).isNull();
    }

    @Test
    void should_fill_missing_level_probabilities_with_zero() {

        // given
        OllamaDecisionModel model = model(MockHttpClient.thatAlwaysResponds(ok(answers(
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
        OllamaDecisionModel model = model(MockHttpClient.thatAlwaysResponds(ok(answers(
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

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void should_reject_responses_that_do_not_match_the_request(String body, String expectedMessage) {

        OllamaDecisionModel model = model(MockHttpClient.thatAlwaysResponds(ok(body)));

        assertThatThrownBy(() -> model.decide(REQUEST))
                .isInstanceOf(InvalidDecisionResponseException.class)
                .hasMessageContaining(expectedMessage);
    }

    static List<Arguments> invalidResponses() {
        String urgent = "\"urgent\": {\"type\": \"noul\", \"noul\": 0.9}";
        String frustration = "\"frustration\": {\"type\": \"score\", \"score\": 1.0}";
        String team = "\"team\": {\"type\": \"choice\", \"choice\": \"billing\"}";
        return List.of(
                Arguments.of("{\"model\": \"nimble\"}", "no answer to question"),
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
                        "invalid confidence: 1.5"),
                Arguments.of(
                        answers(urgent, team, "\"frustration\": {\"type\": \"score\", \"score\": \"NaN\"}"),
                        "invalid score: NaN"));
    }

    private static String answers(String... answers) {
        return "{\"model\": \"nimble\", \"answers\": {" + String.join(", ", answers) + "}}";
    }

    private static OllamaDecisionModel model(MockHttpClient httpClient) {
        return OllamaDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("nimble")
                .build();
    }

    private static SuccessfulHttpResponse ok(String body) {
        return SuccessfulHttpResponse.builder().statusCode(200).body(body).build();
    }

    static class OtherModelParameters extends DefaultDecisionRequestParameters {

        OtherModelParameters() {
            super(DefaultDecisionRequestParameters.builder().modelName("other-model"));
        }
    }
}
