package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionInputType;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DefaultDecisionRequestParameters;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.time.Duration;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.LinkedHashMap;
import dev.langchain4j.data.message.PdfFileContent;
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
                    ScaleQuestion.from("How frustrated is the customer?", List.of("Calm", "Frustrated", "Angry")))
            .build();

    @Test
    void should_reject_parameters_of_another_decision_model_without_calling_the_api() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        OpenAiDecisionModel model = model(httpClient);
        DecisionRequest request =
                REQUEST.toBuilder().parameters(new OtherModelParameters()).build();

        // when-then
        assertThatThrownBy(() -> model.decide(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OtherModelParameters")
                .hasMessageContaining("OpenAiDecisionRequestParameters");
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
        OpenAiDecisionModel model = OpenAiDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("gpt-6-luna")
                .customParameters(Map.of(
                        "new_parameter", "value",
                        "model", "custom-model",
                        "input", "custom input",
                        "questions", List.of("custom question")))
                .build();

        // when
        model.decide(REQUEST);

        // then
        String body = httpClient.requests().get(0).body();
        assertThat(OBJECT_MAPPER.readValue(body, Map.class))
                .containsEntry("new_parameter", "value")
                .containsEntry("model", "custom-model")
                .containsEntry("input", "custom input")
                .containsEntry("questions", List.of("custom question"));
        assertThat(body).containsOnlyOnce("\"model\"");
        assertThat(body).containsOnlyOnce("\"input\"");
        assertThat(body).containsOnlyOnce("\"questions\"");
    }

    @Test
    void should_replace_builder_custom_parameters_per_request() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        OpenAiDecisionModel model = OpenAiDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("gpt-6-luna")
                .customParameters(Map.of("a", "1", "b", "2"))
                .build();

        // when
        model.decide(REQUEST.toBuilder()
                .parameters(OpenAiDecisionRequestParameters.builder()
                        .customParameters(Map.of("b", "3"))
                        .build())
                .build());
        model.decide(REQUEST.toBuilder()
                .parameters(OpenAiDecisionRequestParameters.builder().customParameters(Map.of()).build())
                .build());
        model.decide(REQUEST.toBuilder()
                .parameters(DecisionRequestParameters.builder().modelName("other-model").build())
                .build());

        // then
        assertThat(OBJECT_MAPPER.readValue(httpClient.requests().get(0).body(), Map.class))
                .containsEntry("b", "3")
                .doesNotContainKey("a");
        assertThat(OBJECT_MAPPER.readValue(httpClient.requests().get(1).body(), Map.class))
                .containsEntry("a", "1")
                .containsEntry("b", "2");
        assertThat(OBJECT_MAPPER.readValue(httpClient.requests().get(2).body(), Map.class))
                .containsEntry("a", "1")
                .containsEntry("b", "2")
                .containsEntry("model", "other-model");
    }

    @Test
    void should_send_prompt_cache_key_set_on_builder_and_replace_it_per_request() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        OpenAiDecisionModel model = OpenAiDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("gpt-6-luna")
                .promptCacheKey("builder-key")
                .build();

        // when
        model.decide(REQUEST);
        model.decide(REQUEST.toBuilder()
                .parameters(OpenAiDecisionRequestParameters.builder()
                        .promptCacheKey("request-key")
                        .build())
                .build());
        model.decide(REQUEST.toBuilder()
                .parameters(DecisionRequestParameters.builder().modelName("other-model").build())
                .build());
        model.decideAsync(REQUEST).join();

        // then
        assertThat(model.defaultRequestParameters().promptCacheKey()).isEqualTo("builder-key");
        assertThat(OBJECT_MAPPER.readValue(httpClient.requests().get(0).body(), Map.class))
                .containsEntry("prompt_cache_key", "builder-key");
        assertThat(OBJECT_MAPPER.readValue(httpClient.requests().get(1).body(), Map.class))
                .containsEntry("prompt_cache_key", "request-key");
        assertThat(OBJECT_MAPPER.readValue(httpClient.requests().get(2).body(), Map.class))
                .containsEntry("prompt_cache_key", "builder-key")
                .containsEntry("model", "other-model");
        assertThat(OBJECT_MAPPER.readValue(httpClient.requests().get(3).body(), Map.class))
                .containsEntry("prompt_cache_key", "builder-key");
    }

    @Test
    void should_not_send_prompt_cache_key_by_default() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));

        // when
        model(httpClient).decide(REQUEST);

        // then
        assertThat(OBJECT_MAPPER.readValue(httpClient.requests().get(0).body(), Map.class))
                .doesNotContainKey("prompt_cache_key");
    }

    @Test
    void should_replace_prompt_cache_key_with_custom_parameter_of_the_same_name() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        OpenAiDecisionModel model = OpenAiDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("gpt-6-luna")
                .promptCacheKey("typed-key")
                .customParameters(Map.of("prompt_cache_key", "custom-key"))
                .build();

        // when
        model.decide(REQUEST);

        // then
        String body = httpClient.requests().get(0).body();
        assertThat(OBJECT_MAPPER.readValue(body, Map.class)).containsEntry("prompt_cache_key", "custom-key");
        assertThat(body).containsOnlyOnce("\"prompt_cache_key\"");
    }

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
    void should_send_level_descriptions() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "gpt-6-luna", "answers": [{"type": "score", "name": "severity", "score": 1.0,
                 "probabilities": [], "confidence": 1.0}]}
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
                                        .level("Critical", "No customer can use the product")
                                        .build())
                        .build());

        // then
        assertThat(OBJECT_MAPPER.readTree(httpClient.request().body()).get("questions").get(0).get("levels"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [{"label": "Minor"}, {"label": "Critical", "description": "No customer can use the product"}]
                        """));
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
    void should_send_map_input_as_labeled_text_parts() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "urgent", "probability": 0.9}]}
                """));

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .inputFields(Map.of("ticket", "My payouts are failing"))
                        .question("urgent", YesNoQuestion.from("Does this need attention today?"))
                        .build());

        // then
        JsonNode body = OBJECT_MAPPER.readTree(httpClient.request().body());
        assertThat(body.get("input"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [{
                          "type": "message",
                          "role": "user",
                          "content": [{"type": "input_text", "text": "ticket: \\"My payouts are failing\\""}]
                        }]
                        """));
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
                        .inputContents(List.of(
                                TextContent.from("The package arrived like this."),
                                ImageContent.from("iVBORw0KGgo=", "image/png", ImageContent.DetailLevel.HIGH)))
                        .question("damaged", YesNoQuestion.from("Is the item damaged?"))
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
    void should_send_map_with_contents_as_labeled_parts() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "damaged", "probability": 0.95}]}
                """));
        ImageContent photo = ImageContent.from("iVBORw0KGgo=", "image/png", ImageContent.DetailLevel.LOW);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("comment", "Arrived like this");
        input.put("photo", photo);

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .inputFields(input)
                        .question("damaged", YesNoQuestion.from("Is the item damaged?"))
                        .build());

        // then
        assertThat(OBJECT_MAPPER.readTree(httpClient.request().body()).get("input"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [{
                          "type": "message",
                          "role": "user",
                          "content": [
                            {"type": "input_text", "text": "comment: \\"Arrived like this\\""},
                            {"type": "input_text", "text": "photo:"},
                            {"type": "input_image", "image_url": "data:image/png;base64,iVBORw0KGgo=", "detail": "low"}
                          ]
                        }]
                        """));
    }

    @Test
    void should_send_list_of_images_after_their_name_and_reject_other_contents() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "damaged", "probability": 0.95}]}
                """));
        ImageContent front = ImageContent.from("iVBORw0KGgo=", "image/png", ImageContent.DetailLevel.LOW);
        ImageContent back = ImageContent.from("R0lGODlh", "image/gif", ImageContent.DetailLevel.LOW);

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .inputFields(Map.of("photos", List.of(front, back)))
                        .question("damaged", YesNoQuestion.from("Is the item damaged?"))
                        .build());

        // then
        assertThat(OBJECT_MAPPER.readTree(httpClient.request().body()).get("input").get(0).get("content"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [
                          {"type": "input_text", "text": "photos:"},
                          {"type": "input_image", "image_url": "data:image/png;base64,iVBORw0KGgo=", "detail": "low"},
                          {"type": "input_image", "image_url": "data:image/gif;base64,R0lGODlh", "detail": "low"}
                        ]
                        """));
        assertThatThrownBy(() -> model(httpClient)
                        .decide(DecisionRequest.builder()
                                .inputFields(Map.of("invoice", PdfFileContent.from("JVBERi0=", "application/pdf")))
                                .question("damaged", YesNoQuestion.from("Is the item damaged?"))
                                .build()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("PDF");
        assertThat(httpClient.requests()).hasSize(1);
    }

    @Test
    void should_send_images_referenced_by_url_as_is() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "damaged", "probability": 0.95}]}
                """));

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .inputContents(List.of(ImageContent.from("https://example.com/package.png")))
                        .question("damaged", YesNoQuestion.from("Is the item damaged?"))
                        .build());

        // then
        assertThat(OBJECT_MAPPER.readTree(httpClient.request().body()).get("input").get(0).get("content"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [{"type": "input_image", "image_url": "https://example.com/package.png", "detail": "low"}]
                        """));
    }

    @Test
    void should_send_conversation_as_transcript() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "resolved", "probability": 0.9}]}
                """));
        ToolExecutionRequest getOrder = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getOrder")
                .arguments("{\"id\":42}")
                .build();
        ToolExecutionRequest getCustomer = ToolExecutionRequest.builder()
                .id("call_2")
                .name("getCustomer")
                .build();

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .inputMessages(
                                SystemMessage.from("You are a billing support agent."),
                                UserMessage.from("Anna", "Where is my order 42?"),
                                AiMessage.builder()
                                        .text("Let me check.")
                                        .thinking("The user mentions order 42.")
                                        .toolExecutionRequests(List.of(getOrder, getCustomer))
                                        .build(),
                                ToolExecutionResultMessage.from(getOrder, "{\"status\":\"shipped\"}"),
                                ToolExecutionResultMessage.builder()
                                        .id("call_2")
                                        .toolName("getCustomer")
                                        .text("timeout")
                                        .isError(true)
                                        .build(),
                                AiMessage.from("Your order has shipped."))
                        .question("resolved", YesNoQuestion.from("Did the assistant answer the question?"))
                        .build());

        // then
        JsonNode input = OBJECT_MAPPER.readTree(httpClient.request().body()).get("input");
        assertThat(input).hasSize(1);
        assertThat(input.get(0).get("role").asText()).isEqualTo("user");
        JsonNode content = input.get(0).get("content");
        assertThat(content).hasSize(1);
        String text = content.get(0).get("text").asText();
        assertThat(text).startsWith("messages: ");
        assertThat(OBJECT_MAPPER.readTree(text.substring("messages: ".length())))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [
                          {"role": "system", "text": "You are a billing support agent."},
                          {"role": "user", "name": "Anna", "text": "Where is my order 42?"},
                          {
                            "role": "assistant",
                            "text": "Let me check.",
                            "tool_calls": [
                              {"id": "call_1", "name": "getOrder", "arguments": "{\\"id\\":42}"},
                              {"id": "call_2", "name": "getCustomer"}
                            ]
                          },
                          {"role": "tool", "tool_call_id": "call_1", "name": "getOrder", "text": "{\\"status\\":\\"shipped\\"}"},
                          {"role": "tool", "tool_call_id": "call_2", "name": "getCustomer", "text": "timeout", "is_error": true},
                          {"role": "assistant", "text": "Your order has shipped."}
                        ]
                        """));
    }

    @Test
    void should_send_images_of_conversation_after_transcript() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "resolved", "probability": 0.9}]}
                """));
        ImageContent photo = ImageContent.from("iVBORw0KGgo=", "image/png", ImageContent.DetailLevel.LOW);

        // when
        model(httpClient)
                .decide(DecisionRequest.builder()
                        .inputMessages(UserMessage.from(TextContent.from("It arrived like this:"), photo))
                        .question("resolved", YesNoQuestion.from("Is the item damaged?"))
                        .build());

        // then
        JsonNode content = OBJECT_MAPPER.readTree(httpClient.request().body()).get("input").get(0).get("content");
        assertThat(content).hasSize(3);
        assertThat(OBJECT_MAPPER.readTree(content.get(0).get("text").asText().substring("messages: ".length())))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [{"role": "user", "text": "It arrived like this:\\n[image 1]"}]
                        """));
        assertThat(content.get(1)).isEqualTo(OBJECT_MAPPER.readTree(
                """
                {"type": "input_text", "text": "[image 1]:"}
                """));
        assertThat(content.get(2).get("type").asText()).isEqualTo("input_image");
        assertThat(content.get(2).get("image_url").asText()).isEqualTo("data:image/png;base64,iVBORw0KGgo=");
    }

    @Test
    void should_reject_conversation_with_nothing_to_send_without_calling_the_api() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));
        DecisionRequest request = DecisionRequest.builder()
                .inputMessages(List.of(AiMessage.builder().thinking("The user did not write anything.").build()))
                .question("urgent", YesNoQuestion.from("Does this need attention today?"))
                .build();

        // when-then
        assertThatThrownBy(() -> model(httpClient).decide(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The conversation has nothing to send: AiMessages without text and tool execution "
                        + "requests are skipped");
        assertThat(model(httpClient).decideAsync(request))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
        assertThat(httpClient.requests()).isEmpty();
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
                .hasMessageStartingWith("The OpenAI Decisions API does not support ");
        assertThat(model(httpClient).decideAsync(request))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(UnsupportedFeatureException.class);
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_unsupported_messages_and_contents_without_calling_the_api() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(RESPONSE));

        // when-then
        assertThatThrownBy(() -> model(httpClient)
                        .decide(DecisionRequest.builder()
                                .inputMessages(List.of(UserMessage.from("Hi"), CustomMessage.from(Map.of("key", "value"))))
                                .question("urgent", YesNoQuestion.from("Does this need attention today?"))
                                .build()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("The OpenAI Decisions API does not support CustomMessage in a conversation. Supported "
                        + "messages: SystemMessage, UserMessage, AiMessage and ToolExecutionResultMessage");
        assertThat(model(httpClient)
                        .decideAsync(DecisionRequest.builder()
                                .inputMessages(List.of(UserMessage.from(PdfFileContent.from("JVBERi0=", "application/pdf"))))
                                .question("urgent", YesNoQuestion.from("Does this need attention today?"))
                                .build()))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(UnsupportedFeatureException.class)
                .withMessageContaining("The OpenAI Decisions API supports only text and images in a conversation, but it contains PDF");
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
                .question("urgent", YesNoQuestion.from("Does this need attention today?"))
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
                .question("frustration", ScaleQuestion.from("How frustrated?", List.of("Calm", "Frustrated", "Angry")))
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
                                .inputContents(List.of(ImageContent.from("iVBORw0KGgo=", "image/png", detailLevel)))
                                .question("damaged", YesNoQuestion.from("Is the item damaged?"))
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

    static class OtherModelParameters extends DefaultDecisionRequestParameters {

        OtherModelParameters() {
            super(DefaultDecisionRequestParameters.builder().modelName("other-model"));
        }
    }
}
