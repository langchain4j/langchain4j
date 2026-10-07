package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.List;
import java.util.Map;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class OpenAiOfficialDecisionModelTest {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static final String RESPONSE =
            """
            {
              "model": "gpt-6-luna",
              "answers": [
                {"type": "predicate", "name": "urgent", "probability": 0.93},
                {
                  "type": "choice",
                  "name": "team",
                  "choice": "billing",
                  "probabilities": [{"value": "billing", "probability": 0.88}, {"value": "support", "probability": 0.12}],
                  "confidence": 0.8
                },
                {
                  "type": "score",
                  "name": "frustration",
                  "score": 1.4,
                  "probabilities": [
                    {"value": 0, "label": "Calm", "probability": 0.05},
                    {"value": 1, "label": "Frustrated", "probability": 0.5},
                    {"value": 2, "label": "Angry", "probability": 0.45}
                  ],
                  "confidence": 0.6
                }
              ],
              "usage": {
                "input_tokens": 42,
                "input_tokens_details": {"cached_tokens": 10, "cache_write_tokens": 0},
                "output_tokens": 0,
                "output_tokens_details": {"reasoning_tokens": 0},
                "total_tokens": 42
              }
            }
            """;

    static final DecisionRequest REQUEST = DecisionRequest.builder()
            .input("Help! My payouts have been failing for 3 days.")
            .question(
                    "urgent",
                    YesNoQuestion.builder()
                            .text("Does this need attention today?")
                            .yesWhen("Money is blocked")
                            .build())
            .question(
                    "team",
                    ChoiceQuestion.builder()
                            .text("Which team should handle this ticket?")
                            .option("billing", "Payments, payouts, invoices")
                            .option("support", "Problems using the product")
                            .build())
            .question(
                    "frustration",
                    ScaleQuestion.of("How frustrated is the customer?", List.of("Calm", "Frustrated", "Angry")))
            .build();

    final OpenAiOfficialStubHttpClient httpClient = new OpenAiOfficialStubHttpClient();

    @Test
    void should_send_request_in_openai_format_and_map_answers() throws Exception {

        // given
        httpClient.enqueue("decisions", RESPONSE);

        // when
        DecisionResponse response = model().decide(REQUEST);

        // then
        assertThat(OBJECT_MAPPER.readTree(httpClient.requestTo("decisions").body()))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        {
                          "model": "gpt-6-luna",
                          "input": "Help! My payouts have been failing for 3 days.",
                          "questions": [
                            {
                              "type": "predicate",
                              "name": "urgent",
                              "instructions": "Does this need attention today?\\nAnswer yes when: Money is blocked"
                            },
                            {
                              "type": "choice",
                              "name": "team",
                              "instructions": "Which team should handle this ticket?",
                              "choices": [
                                {"value": "billing", "description": "Payments, payouts, invoices"},
                                {"value": "support", "description": "Problems using the product"}
                              ]
                            },
                            {
                              "type": "score",
                              "name": "frustration",
                              "instructions": "How frustrated is the customer?",
                              "levels": [{"label": "Calm"}, {"label": "Frustrated"}, {"label": "Angry"}]
                            }
                          ]
                        }
                        """));
        assertThat(response.yesNo("urgent").probability()).isEqualTo(0.93);
        assertThat(response.choice("team").value()).isEqualTo("billing");
        assertThat(response.choice("team").probabilities())
                .containsExactly(Map.entry("billing", 0.88), Map.entry("support", 0.12));
        assertThat(response.scale("frustration").mean()).isEqualTo(1.4);
        assertThat(response.scale("frustration").probabilities()).containsExactly(0.05, 0.5, 0.45);
        assertThat(response.modelName()).isEqualTo("gpt-6-luna");
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(42);
    }

    @Test
    void should_send_level_descriptions() throws Exception {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {"model": "gpt-6-luna", "answers": [{"type": "score", "name": "severity", "score": 1.0,
                 "probabilities": [], "confidence": 1.0}]}
                """);

        // when
        model().decide(DecisionRequest.builder()
                .input("The whole product is down.")
                .question(
                        "severity",
                        ScaleQuestion.builder()
                                .text("How severe is the incident?")
                                .level("Minor")
                                .level("Critical", "No customer can use the product")
                                .build())
                .build());

        // then
        assertThat(OBJECT_MAPPER
                        .readTree(httpClient.requestTo("decisions").body())
                        .get("questions")
                        .get(0)
                        .get("levels"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [{"label": "Minor"}, {"label": "Critical", "description": "No customer can use the product"}]
                        """));
    }

    @Test
    void should_decide_asynchronously() {

        // given
        httpClient.enqueue("decisions", RESPONSE);

        // when
        DecisionResponse response = model().decideAsync(REQUEST).join();

        // then
        assertThat(response.choice("team").value()).isEqualTo("billing");
    }

    @Test
    void should_send_contents_as_user_message_with_inline_image() throws Exception {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "damaged", "probability": 0.95}],
                 "usage": {"input_tokens": 90, "input_tokens_details": {"cached_tokens": 0, "cache_write_tokens": 0},
                           "output_tokens": 0, "output_tokens_details": {"reasoning_tokens": 0}, "total_tokens": 90}}
                """);

        // when
        model().decide(DecisionRequest.builder()
                .input(List.of(
                        TextContent.from("The package arrived like this."),
                        ImageContent.from("iVBORw0KGgo=", "image/png", ImageContent.DetailLevel.HIGH)))
                .question("damaged", YesNoQuestion.of("Is the item damaged?"))
                .build());

        // then
        assertThat(OBJECT_MAPPER.readTree(httpClient.requestTo("decisions").body()).get("input"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [{
                          "role": "user",
                          "content": [
                            {"type": "input_text", "text": "The package arrived like this."},
                            {"type": "input_image", "image_url": "data:image/png;base64,iVBORw0KGgo=", "detail": "high"}
                          ],
                          "type": "message"
                        }]
                        """));
    }

    @Test
    void should_send_map_with_contents_as_labeled_parts() throws Exception {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "damaged", "probability": 0.95}]}
                """);
        ImageContent photo = ImageContent.from("iVBORw0KGgo=", "image/png", ImageContent.DetailLevel.LOW);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("comment", "Arrived like this");
        input.put("photo", photo);

        // when
        model().decide(DecisionRequest.builder()
                .input(input)
                .question("damaged", YesNoQuestion.of("Is the item damaged?"))
                .build());

        // then
        assertThat(OBJECT_MAPPER.readTree(httpClient.requestTo("decisions").body()).get("input"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [{
                          "role": "user",
                          "content": [
                            {"type": "input_text", "text": "comment: \\"Arrived like this\\""},
                            {"type": "input_text", "text": "photo:"},
                            {"type": "input_image", "image_url": "data:image/png;base64,iVBORw0KGgo=", "detail": "low"}
                          ],
                          "type": "message"
                        }]
                        """));
    }

    @Test
    void should_reject_images_referenced_by_url_without_calling_the_api() {

        assertThatThrownBy(() -> model().decide(DecisionRequest.builder()
                        .input(List.of(ImageContent.from("https://example.com/package.png")))
                        .question("damaged", YesNoQuestion.of("Is the item damaged?"))
                        .build()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("only inline images");
        assertThat(httpClient.recordedRequests()).isEmpty();
    }

    @Test
    void should_return_refusals_and_the_other_answers() {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {
                  "model": "gpt-6-luna",
                  "answers": [
                    {"type": "refusal", "name": "urgent"},
                    {"type": "choice", "name": "team", "choice": "billing", "probabilities": [], "confidence": 0.7},
                    {"type": "score", "name": "frustration", "score": 1.0, "probabilities": [], "confidence": 0.5}
                  ],
                  "usage": {"input_tokens": 42, "input_tokens_details": {"cached_tokens": 0, "cache_write_tokens": 0},
                            "output_tokens": 0, "output_tokens_details": {"reasoning_tokens": 0}, "total_tokens": 42}
                }
                """);

        // when
        DecisionResponse response = model().decide(REQUEST);

        // then
        assertThat(response.isRefused("urgent")).isTrue();
        assertThatThrownBy(() -> response.yesNo("urgent")).isInstanceOf(ContentFilteredException.class);
        assertThat(response.choice("team").value()).isEqualTo("billing");
    }

    static final String ERROR = "{\"error\": {\"message\": \"error\", \"type\": \"invalid_request_error\"}}";

    @ParameterizedTest
    @MethodSource("errors")
    void should_map_error_responses_to_langchain4j_exceptions(int statusCode, Class<? extends Exception> exception) {

        // given
        httpClient.enqueue("decisions", statusCode, ERROR);
        httpClient.enqueue("decisions", statusCode, ERROR);

        // when-then
        assertThatThrownBy(() -> model().decide(REQUEST)).isInstanceOf(exception);
        assertThatThrownBy(() -> model().decideAsync(REQUEST).join()).hasCauseInstanceOf(exception);
    }

    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(400, InvalidRequestException.class),
                Arguments.of(401, AuthenticationException.class),
                Arguments.of(404, ModelNotFoundException.class));
    }

    @Test
    void should_absorb_rounding_errors_and_accept_missing_usage() {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {
                  "model": "gpt-6-luna",
                  "answers": [
                    {"type": "predicate", "name": "urgent", "probability": 1.0000000002},
                    {"type": "choice", "name": "team", "choice": "billing",
                     "probabilities": [{"value": "billing", "probability": 1.0000000001}], "confidence": 1.0000000001},
                    {"type": "score", "name": "frustration", "score": 2.0000000004, "confidence": 1.0,
                     "probabilities": [{"value": 2, "label": "Angry", "probability": 1.0000000003}]}
                  ]
                }
                """);

        // when
        DecisionResponse response = model().decide(REQUEST);

        // then
        assertThat(response.yesNo("urgent").probability()).isEqualTo(1.0);
        assertThat(response.choice("team").probabilityOf("billing")).isEqualTo(1.0);
        assertThat(response.scale("frustration").mean()).isEqualTo(2.0);
        assertThat(response.scale("frustration").probabilities()).containsExactly(0.0, 0.0, 1.0);
        assertThat(response.tokenUsage()).isNull();
    }

    @Test
    void should_fail_with_invalid_decision_response_exception_when_an_answer_is_malformed() {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "urgent"}]}
                """);
        DecisionRequest request = DecisionRequest.builder()
                .input("Help!")
                .question("urgent", YesNoQuestion.of("Does this need attention today?"))
                .build();

        // when-then
        assertThatThrownBy(() -> model().decide(request)).isInstanceOf(InvalidDecisionResponseException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ImageContent.DetailLevel.class, names = {"MEDIUM", "ULTRA_HIGH"})
    void should_reject_unsupported_image_detail_levels_without_calling_the_api(ImageContent.DetailLevel detailLevel) {

        assertThatThrownBy(() -> model().decide(DecisionRequest.builder()
                        .input(List.of(ImageContent.from("iVBORw0KGgo=", "image/png", detailLevel)))
                        .question("damaged", YesNoQuestion.of("Is the item damaged?"))
                        .build()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining(detailLevel.name());
        assertThat(httpClient.recordedRequests()).isEmpty();
    }

    @Test
    void should_require_model_name() {

        OpenAiOfficialDecisionModel model = OpenAiOfficialDecisionModel.builder()
                .openAIClient(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(httpClient)
                        .build()))
                .build();

        assertThatThrownBy(() -> model.decide(REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The model name must be set");
    }

    private OpenAiOfficialDecisionModel model() {
        return OpenAiOfficialDecisionModel.builder()
                .openAIClient(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(httpClient)
                        .build()))
                .modelName("gpt-6-luna")
                .build();
    }
}
