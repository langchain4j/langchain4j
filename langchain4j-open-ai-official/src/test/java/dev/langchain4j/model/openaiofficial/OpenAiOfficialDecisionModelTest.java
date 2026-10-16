package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
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
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionInputType;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DefaultDecisionRequestParameters;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.time.Duration;
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
import dev.langchain4j.data.message.PdfFileContent;
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
    void should_reject_parameters_of_another_decision_model_without_calling_the_api() {

        // given
        OpenAiOfficialDecisionModel model = model();
        DecisionRequest request =
                REQUEST.toBuilder().parameters(new OtherModelParameters()).build();

        // when-then
        assertThatThrownBy(() -> model.decide(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OtherModelParameters")
                .hasMessageContaining("OpenAiOfficialDecisionRequestParameters");
        assertThat(model.decideAsync(request))
                .failsWithin(Duration.ZERO)
                .withThrowableOfType(ExecutionException.class)
                .withCauseExactlyInstanceOf(IllegalArgumentException.class);
        assertThat(httpClient.recordedRequests()).isEmpty();
    }

    @Test
    void should_send_custom_parameters_set_on_builder() throws Exception {

        // given
        httpClient.enqueue("decisions", RESPONSE);
        OpenAiOfficialDecisionModel model = OpenAiOfficialDecisionModel.builder()
                .openAIClient(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(httpClient)
                        .build()))
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
        String body = httpClient.recordedRequests().get(0).body();
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
        httpClient.enqueue("decisions", RESPONSE);
        httpClient.enqueue("decisions", RESPONSE);
        httpClient.enqueue("decisions", RESPONSE);
        OpenAiOfficialDecisionModel model = OpenAiOfficialDecisionModel.builder()
                .openAIClient(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(httpClient)
                        .build()))
                .modelName("gpt-6-luna")
                .customParameters(Map.of("a", "1", "b", "2"))
                .build();

        // when
        model.decide(REQUEST.toBuilder()
                .parameters(OpenAiOfficialDecisionRequestParameters.builder()
                        .customParameters(Map.of("b", "3"))
                        .build())
                .build());
        model.decide(REQUEST.toBuilder()
                .parameters(OpenAiOfficialDecisionRequestParameters.builder().customParameters(Map.of()).build())
                .build());
        model.decide(REQUEST.toBuilder()
                .parameters(DecisionRequestParameters.builder().modelName("other-model").build())
                .build());

        // then
        assertThat(OBJECT_MAPPER.readValue(httpClient.recordedRequests().get(0).body(), Map.class))
                .containsEntry("b", "3")
                .doesNotContainKey("a");
        assertThat(OBJECT_MAPPER.readValue(httpClient.recordedRequests().get(1).body(), Map.class))
                .containsEntry("a", "1")
                .containsEntry("b", "2");
        assertThat(OBJECT_MAPPER.readValue(httpClient.recordedRequests().get(2).body(), Map.class))
                .containsEntry("a", "1")
                .containsEntry("b", "2")
                .containsEntry("model", "other-model");
    }

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
                .inputContents(List.of(
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
    void should_send_map_input_as_labeled_text_parts() throws Exception {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "urgent", "probability": 0.9}]}
                """);

        // when
        model().decide(DecisionRequest.builder()
                .inputFields(Map.of("ticket", "My payouts are failing"))
                .question("urgent", YesNoQuestion.of("Does this need attention today?"))
                .build());

        // then
        assertThat(OBJECT_MAPPER.readTree(httpClient.requestTo("decisions").body()).get("input"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [{
                          "role": "user",
                          "content": [{"type": "input_text", "text": "ticket: \\"My payouts are failing\\""}],
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
                .inputFields(input)
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
    void should_send_list_of_images_after_their_name_and_reject_other_contents() throws Exception {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "damaged", "probability": 0.95}]}
                """);
        ImageContent front = ImageContent.from("iVBORw0KGgo=", "image/png", ImageContent.DetailLevel.LOW);
        ImageContent back = ImageContent.from("R0lGODlh", "image/gif", ImageContent.DetailLevel.LOW);

        // when
        model().decide(DecisionRequest.builder()
                .inputFields(Map.of("photos", List.of(front, back)))
                .question("damaged", YesNoQuestion.of("Is the item damaged?"))
                .build());

        // then
        assertThat(OBJECT_MAPPER
                        .readTree(httpClient.requestTo("decisions").body())
                        .get("input")
                        .get(0)
                        .get("content"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [
                          {"type": "input_text", "text": "photos:"},
                          {"type": "input_image", "image_url": "data:image/png;base64,iVBORw0KGgo=", "detail": "low"},
                          {"type": "input_image", "image_url": "data:image/gif;base64,R0lGODlh", "detail": "low"}
                        ]
                        """));
        assertThatThrownBy(() -> model().decide(DecisionRequest.builder()
                        .inputFields(Map.of("invoice", PdfFileContent.from("JVBERi0=", "application/pdf")))
                        .question("damaged", YesNoQuestion.of("Is the item damaged?"))
                        .build()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("PDF");
        assertThat(httpClient.recordedRequests()).hasSize(1);
    }

    @Test
    void should_send_images_referenced_by_url_as_is() throws Exception {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "damaged", "probability": 0.95}]}
                """);

        // when
        model().decide(DecisionRequest.builder()
                .inputContents(List.of(ImageContent.from("https://example.com/package.png")))
                .question("damaged", YesNoQuestion.of("Is the item damaged?"))
                .build());

        // then
        assertThat(OBJECT_MAPPER
                        .readTree(httpClient.requestTo("decisions").body())
                        .get("input")
                        .get(0)
                        .get("content"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [{"type": "input_image", "image_url": "https://example.com/package.png", "detail": "low"}]
                        """));
    }

    @Test
    void should_send_messages_as_conversation_items() throws Exception {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "resolved", "probability": 0.9}],
                 "usage": {"input_tokens": 90, "input_tokens_details": {"cached_tokens": 0, "cache_write_tokens": 0},
                           "output_tokens": 0, "output_tokens_details": {"reasoning_tokens": 0}, "total_tokens": 90}}
                """);
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getOrder")
                .arguments("{\"id\":42}")
                .build();

        // when
        model().decide(DecisionRequest.builder()
                .inputMessages(List.of(
                        SystemMessage.from("You are a billing support agent."),
                        UserMessage.from("I was charged twice for order 42."),
                        AiMessage.builder()
                                .text("Let me check.")
                                .thinking("The user mentions order 42.")
                                .toolExecutionRequests(List.of(toolCall))
                                .build(),
                        ToolExecutionResultMessage.from(toolCall, "{\"charges\":[19.99,19.99]}"),
                        AiMessage.from("I have refunded one of the charges.")))
                .question("resolved", YesNoQuestion.of("Did the assistant resolve the user's problem?"))
                .build());

        // then
        assertThat(OBJECT_MAPPER.readTree(httpClient.requestTo("decisions").body()).get("input"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [
                          {
                            "type": "message",
                            "role": "system",
                            "content": [{"type": "input_text", "text": "You are a billing support agent."}]
                          },
                          {
                            "type": "message",
                            "role": "user",
                            "content": [{"type": "input_text", "text": "I was charged twice for order 42."}]
                          },
                          {
                            "type": "message",
                            "role": "assistant",
                            "content": [{"type": "output_text", "text": "Let me check."}]
                          },
                          {"type": "function_call", "call_id": "call_1", "name": "getOrder", "arguments": "{\\"id\\":42}"},
                          {"type": "function_call_output", "call_id": "call_1", "output": "{\\"charges\\":[19.99,19.99]}"},
                          {
                            "type": "message",
                            "role": "assistant",
                            "content": [{"type": "output_text", "text": "I have refunded one of the charges."}]
                          }
                        ]
                        """));
    }

    @Test
    void should_send_parallel_tool_calls_in_order_and_missing_arguments_as_empty_object() throws Exception {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "answered", "probability": 0.9}],
                 "usage": {"input_tokens": 90, "input_tokens_details": {"cached_tokens": 0, "cache_write_tokens": 0},
                           "output_tokens": 0, "output_tokens_details": {"reasoning_tokens": 0}, "total_tokens": 90}}
                """);
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
        model()
                .decide(DecisionRequest.builder()
                        .inputMessages(List.of(
                                UserMessage.from("Where is my order?"),
                                AiMessage.from(getOrder, getCustomer),
                                ToolExecutionResultMessage.from(getOrder, "shipped"),
                                ToolExecutionResultMessage.from(getCustomer, "Anna")))
                        .question("answered", YesNoQuestion.of("Did the assistant answer the question?"))
                        .build());

        // then
        assertThat(OBJECT_MAPPER.readTree(httpClient.requestTo("decisions").body()).get("input"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [
                          {
                            "type": "message",
                            "role": "user",
                            "content": [{"type": "input_text", "text": "Where is my order?"}]
                          },
                          {"type": "function_call", "call_id": "call_1", "name": "getOrder", "arguments": "{\\"id\\":42}"},
                          {"type": "function_call", "call_id": "call_2", "name": "getCustomer", "arguments": "{}"},
                          {"type": "function_call_output", "call_id": "call_1", "output": "shipped"},
                          {"type": "function_call_output", "call_id": "call_2", "output": "Anna"}
                        ]
                        """));
    }

    @Test
    void should_send_tool_result_with_image_and_user_message_with_image_as_parts() throws Exception {

        // given
        httpClient.enqueue(
                "decisions",
                """
                {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "damaged", "probability": 0.9}],
                 "usage": {"input_tokens": 90, "input_tokens_details": {"cached_tokens": 0, "cache_write_tokens": 0},
                           "output_tokens": 0, "output_tokens_details": {"reasoning_tokens": 0}, "total_tokens": 90}}
                """);
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getDeliveryPhoto")
                .arguments("{}")
                .build();
        ImageContent photo = ImageContent.from("iVBORw0KGgo=", "image/png", ImageContent.DetailLevel.LOW);

        // when
        model().decide(DecisionRequest.builder()
                .inputMessages(List.of(
                        UserMessage.from(TextContent.from("It arrived like this:"), photo),
                        AiMessage.from(toolCall),
                        ToolExecutionResultMessage.builder()
                                .id("call_1")
                                .toolName("getDeliveryPhoto")
                                .contents(TextContent.from("Photo taken at delivery:"), photo)
                                .build()))
                .question("damaged", YesNoQuestion.of("Was the item damaged after the delivery?"))
                .build());

        // then
        assertThat(OBJECT_MAPPER.readTree(httpClient.requestTo("decisions").body()).get("input"))
                .isEqualTo(OBJECT_MAPPER.readTree(
                        """
                        [
                          {
                            "type": "message",
                            "role": "user",
                            "content": [
                              {"type": "input_text", "text": "It arrived like this:"},
                              {"type": "input_image", "image_url": "data:image/png;base64,iVBORw0KGgo=", "detail": "low"}
                            ]
                          },
                          {"type": "function_call", "call_id": "call_1", "name": "getDeliveryPhoto", "arguments": "{}"},
                          {
                            "type": "function_call_output",
                            "call_id": "call_1",
                            "output": [
                              {"type": "input_text", "text": "Photo taken at delivery:"},
                              {"type": "input_image", "image_url": "data:image/png;base64,iVBORw0KGgo=", "detail": "low"}
                            ]
                          }
                        ]
                        """));
    }

    @Test
    void should_reject_conversation_with_nothing_to_send_without_calling_the_api() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .inputMessages(List.of(AiMessage.builder().thinking("The user did not write anything.").build()))
                .question("urgent", YesNoQuestion.of("Does this need attention today?"))
                .build();

        // when-then
        assertThatThrownBy(() -> model().decide(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The conversation has nothing to send: AiMessages without text and tool execution "
                        + "requests are skipped");
        assertThat(model().decideAsync(request))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
        assertThat(httpClient.recordedRequests()).isEmpty();
    }

    @Test
    void should_reject_custom_input_without_calling_the_api() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .input(() -> DecisionInputType.TEXT)
                .question("urgent", YesNoQuestion.of("Does this need attention today?"))
                .build();

        // when-then
        assertThatThrownBy(() -> model().decide(request))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageStartingWith("The OpenAI Decisions API does not support ");
        assertThat(model().decideAsync(request))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(UnsupportedFeatureException.class);
        assertThat(httpClient.recordedRequests()).isEmpty();
    }

    @Test
    void should_reject_unsupported_messages_and_contents_without_calling_the_api() {

        assertThatThrownBy(() -> model().decide(DecisionRequest.builder()
                        .inputMessages(List.of(UserMessage.from("Hi"), CustomMessage.from(Map.of("key", "value"))))
                        .question("urgent", YesNoQuestion.of("Does this need attention today?"))
                        .build()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("The OpenAI Decisions API does not support CustomMessage. Supported messages: "
                        + "SystemMessage, UserMessage, AiMessage and ToolExecutionResultMessage");
        assertThat(model().decideAsync(DecisionRequest.builder()
                        .inputMessages(List.of(UserMessage.from(PdfFileContent.from("JVBERi0=", "application/pdf"))))
                        .question("urgent", YesNoQuestion.of("Does this need attention today?"))
                        .build()))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(UnsupportedFeatureException.class)
                .withMessageContaining("The OpenAI Decisions API supports only text and image input, but the input contains PDF");
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
                        .inputContents(List.of(ImageContent.from("iVBORw0KGgo=", "image/png", detailLevel)))
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

    static class OtherModelParameters extends DefaultDecisionRequestParameters {

        OtherModelParameters() {
            super(DefaultDecisionRequestParameters.builder().modelName("other-model"));
        }
    }
}
