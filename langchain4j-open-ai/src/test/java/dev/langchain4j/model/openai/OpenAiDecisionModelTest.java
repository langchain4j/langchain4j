package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import java.util.List;
import java.util.Map;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class OpenAiDecisionModelTest {

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
                  "probabilities": [
                    {"value": "billing", "probability": 0.88},
                    {"value": "support", "probability": 0.12}
                  ],
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
                            .noWhen("A general question")
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

    @Test
    void should_send_request_in_openai_format_and_map_answers() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));

        // when
        DecisionResponse response = model(httpClient).decide(REQUEST);

        // then
        assertThat(httpClient.request().url()).isEqualTo("https://api.openai.com/v1/decisions");
        assertThat(OBJECT_MAPPER.readTree(httpClient.request().body()))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        {
                          "model": "gpt-6-luna",
                          "input": "Help! My payouts have been failing for 3 days.",
                          "questions": [
                            {
                              "type": "predicate",
                              "name": "urgent",
                              "instructions": "Does this need attention today?\\nAnswer yes when: Money is blocked\\nAnswer no when: A general question"
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
        ChoiceAnswer team = response.choice("team");
        assertThat(team.value()).isEqualTo("billing");
        assertThat(team.probabilities()).containsExactly(Map.entry("billing", 0.88), Map.entry("support", 0.12));
        assertThat(team.confidence()).isEqualTo(0.8);
        ScaleAnswer frustration = response.scale("frustration");
        assertThat(frustration.mean()).isEqualTo(1.4);
        assertThat(frustration.probabilities()).containsExactly(0.05, 0.5, 0.45);
        assertThat(frustration.confidence()).isEqualTo(0.6);
        assertThat(response.modelName()).isEqualTo("gpt-6-luna");
        OpenAiTokenUsage tokenUsage = (OpenAiTokenUsage) response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(42);
        assertThat(tokenUsage.inputTokensDetails().cachedTokens()).isEqualTo(10);
    }

    @Test
    void should_decide_asynchronously() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));

        // when
        DecisionResponse response = model(httpClient).decideAsync(REQUEST).join();

        // then
        assertThat(response.choice("team").value()).isEqualTo("billing");
    }

    @Test
    void should_send_map_input_as_json_text() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "urgent", "probability": 0.9}]}
                """));

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .input(Map.of("ticket", "My payouts are failing"))
                        .question("urgent", YesNoQuestion.of("Does this need attention today?"))
                        .build());

        // then
        JsonNode body = OBJECT_MAPPER.readTree(httpClient.request().body());
        assertThat(body.get("input").asText()).isEqualTo("{\"ticket\":\"My payouts are failing\"}");
    }

    @Test
    void should_send_contents_as_user_message_with_inline_image() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "damaged", "probability": 0.95}]}
                """));

        // when
        DecisionResponse response = model(httpClient)
                .decide(DecisionRequest.builder()
                        .input(List.of(
                                TextContent.from("The package arrived like this."),
                                ImageContent.from("iVBORw0KGgo=", "image/png", ImageContent.DetailLevel.HIGH)))
                        .question("damaged", YesNoQuestion.of("Is the item damaged?"))
                        .build());

        // then
        JsonNode body = OBJECT_MAPPER.readTree(httpClient.request().body());
        assertThat(body.get("input"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [{
                          "type": "message",
                          "role": "user",
                          "content": [
                            {"type": "input_text", "text": "The package arrived like this."},
                            {"type": "input_image", "image_url": "data:image/png;base64,iVBORw0KGgo=", "detail": "high"}
                          ]
                        }]
                        """));
        assertThat(response.yesNo("damaged").probability()).isEqualTo(0.95);
    }

    @Test
    void should_reject_images_referenced_by_url_without_calling_the_api() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));

        // when-then
        assertThatThrownBy(() -> model(httpClient)
                        .decide(DecisionRequest.builder()
                                .input(List.of(ImageContent.from("https://example.com/package.png")))
                                .question("damaged", YesNoQuestion.of("Is the item damaged?"))
                                .build()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("only inline images");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_return_refusals_and_the_other_answers() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {
                  "model": "gpt-6-luna",
                  "answers": [
                    {"type": "refusal", "name": "urgent"},
                    {"type": "choice", "name": "team", "choice": "billing", "probabilities": [], "confidence": 0.7},
                    {"type": "score", "name": "frustration", "score": 1.0, "probabilities": [], "confidence": 0.5}
                  ]
                }
                """));

        // when
        DecisionResponse response = model(httpClient).decide(REQUEST);

        // then
        assertThat(response.isRefused("urgent")).isTrue();
        assertThatThrownBy(() -> response.yesNo("urgent")).isInstanceOf(ContentFilteredException.class);
        assertThat(response.choice("team").value()).isEqualTo("billing");
        assertThat(response.scale("frustration").mean()).isEqualTo(1.0);
    }

    @Test
    void should_absorb_rounding_errors_in_probabilities_and_scores() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {
                  "model": "gpt-6-luna",
                  "answers": [
                    {"type": "predicate", "name": "urgent", "probability": 1.0000000002},
                    {"type": "choice", "name": "team", "choice": "billing",
                     "probabilities": [{"value": "billing", "probability": 1.0000000001}], "confidence": 1.0000000001},
                    {"type": "score", "name": "frustration", "score": 2.0000000004,
                     "probabilities": [{"value": 2, "label": "Angry", "probability": 1.0000000003}], "confidence": 1.0}
                  ]
                }
                """));

        // when
        DecisionResponse response = model(httpClient).decide(REQUEST);

        // then
        assertThat(response.yesNo("urgent").probability()).isEqualTo(1.0);
        assertThat(response.choice("team").probabilityOf("billing")).isEqualTo(1.0);
        assertThat(response.scale("frustration").mean()).isEqualTo(2.0);
        assertThat(response.scale("frustration").probabilities()).containsExactly(0.0, 0.0, 1.0);
    }

    @Test
    void should_fill_level_probabilities_by_level_index() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {
                  "model": "gpt-6-luna",
                  "answers": [
                    {"type": "predicate", "name": "urgent", "probability": 0.5},
                    {"type": "choice", "name": "team", "choice": "billing", "probabilities": [], "confidence": 0.5},
                    {"type": "score", "name": "frustration", "score": 1.8, "confidence": 0.7,
                     "probabilities": [{"value": 2, "label": "Angry", "probability": 0.8},
                                       {"value": 0, "label": "Calm", "probability": 0.2}]}
                  ]
                }
                """));

        // when
        DecisionResponse response = model(httpClient).decide(REQUEST);

        // then
        assertThat(response.scale("frustration").probabilities()).containsExactly(0.2, 0.0, 0.8);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"type\": \"verdict\", \"name\": \"urgent\"}",
                "{\"type\": \"predicate\", \"name\": \"urgent\"}",
                "{\"type\": \"predicate\", \"name\": \"urgent\", \"probability\": 1.5}",
                "{\"type\": \"predicate\", \"name\": \"urgent\", \"probability\": 0.5}, {\"type\": \"predicate\", \"probability\": 0.5}"
            })
    void should_fail_on_invalid_answers(String answers) {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(
                ok("{\"model\": \"gpt-6-luna\", \"answers\": [" + answers + "]}"));
        DecisionRequest request = DecisionRequest.builder()
                .input("Help!")
                .question("urgent", YesNoQuestion.of("Does this need attention today?"))
                .build();

        // when-then
        assertThatThrownBy(() -> model(httpClient).decide(request))
                .isInstanceOf(InvalidDecisionResponseException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "1.5", "\"1\""})
    void should_fail_on_invalid_level_index(String level) {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "gpt-6-luna", "answers": [{"type": "score", "name": "frustration", "score": 1.0,
                 "probabilities": [{"value": %s, "label": "x", "probability": 1.0}], "confidence": 1.0}]}
                """.formatted(level)));
        DecisionRequest request = DecisionRequest.builder()
                .input("Help!")
                .question("frustration", ScaleQuestion.of("How frustrated?", List.of("Calm", "Frustrated", "Angry")))
                .build();

        // when-then
        assertThatThrownBy(() -> model(httpClient).decide(request))
                .isInstanceOf(InvalidDecisionResponseException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ImageContent.DetailLevel.class, names = {"MEDIUM", "ULTRA_HIGH"})
    void should_reject_unsupported_image_detail_levels_without_calling_the_api(ImageContent.DetailLevel detailLevel) {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));

        // when-then
        assertThatThrownBy(() -> model(httpClient)
                        .decide(DecisionRequest.builder()
                                .input(List.of(ImageContent.from("iVBORw0KGgo=", "image/png", detailLevel)))
                                .question("damaged", YesNoQuestion.of("Is the item damaged?"))
                                .build()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining(detailLevel.name());
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_require_model_name() {

        OpenAiDecisionModel model = OpenAiDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(MockHttpClient.thatAlwaysResponds(ok(RESPONSE))))
                .apiKey("test-key")
                .build();

        assertThatThrownBy(() -> model.decide(REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The model name must be set");
    }

    private static OpenAiDecisionModel model(MockHttpClient httpClient) {
        return OpenAiDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName(OpenAiDecisionModelName.GPT_6_LUNA)
                .build();
    }

    private static SuccessfulHttpResponse ok(String body) {
        return SuccessfulHttpResponse.builder().statusCode(200).body(body).build();
    }
}
