package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GoogleGenAiDecisionModelTest {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static final String YES_NO_ANSWER = "{\"answers\":{\"spam\":{\"type\":\"noul\",\"noul\":0.92,\"confidence\":0.92}},"
            + "\"usage\":{\"total_input_tokens\":12,\"total_tokens\":12}}";

    HttpServer server;
    final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    final Deque<Reply> replies = new ArrayDeque<>();

    record RecordedRequest(String method, String path, Map<String, String> headers, String body) {}

    record Reply(int status, String body) {}

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    headers,
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Reply reply;
            synchronized (replies) {
                reply = replies.size() > 1 ? replies.poll() : replies.peek();
            }
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    void reply(int status, String body) {
        synchronized (replies) {
            replies.add(new Reply(status, body));
        }
    }

    GoogleGenAiDecisionModel.Builder modelBuilder() {
        return GoogleGenAiDecisionModel.builder()
                .apiEndpoint("http://127.0.0.1:" + server.getAddress().getPort())
                .apiKey("test-key")
                .modelName("gemini-decision-1")
                .maxRetries(0);
    }

    GoogleGenAiDecisionModel model() {
        return modelBuilder().build();
    }

    static JsonNode json(String json) throws IOException {
        return OBJECT_MAPPER.readTree(json);
    }

    JsonNode lastRequestBody() throws IOException {
        return json(requests.get(requests.size() - 1).body());
    }

    static DecisionRequest spamRequest() {
        return DecisionRequest.builder()
                .input("Buy cheap watches now!")
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build();
    }

    @Test
    void should_send_request_to_v1beta_decisions_with_api_key_and_map_every_question_type() throws IOException {
        reply(200, """
                {
                  "answers": {
                    "refund": {"type": "noul", "noul": 0.97, "confidence": 0.97},
                    "topic": {"type": "choice", "choice": "billing", "confidence": 0.9,
                              "probabilities": {"billing": 0.9, "shipping": 0.06, "other": 0.04}},
                    "urgency": {"type": "score", "score": 1.5, "confidence": 0.55,
                                "probabilities": {"0": 0.05, "2": 0.55, "1": 0.4},
                                "legend": {"0": "low", "2": "high", "1": "medium"}}
                  },
                  "usage": {"total_input_tokens": 248, "total_output_tokens": 0, "total_tokens": 248,
                            "total_cached_tokens": 100, "total_thought_tokens": 0, "total_tool_use_tokens": 0,
                            "initial_input_tokens": 200}
                }
                """);
        DecisionRequest request = DecisionRequest.builder()
                .input("My card was charged twice, give me my money back!")
                .question(
                        "refund",
                        YesNoQuestion.builder()
                                .text("Does the customer ask for a refund?")
                                .yesWhen("they ask for money back")
                                .noWhen("anything else")
                                .build())
                .question(
                        "topic",
                        ChoiceQuestion.from(
                                "What is the message about?",
                                Map.of("billing", "payments", "shipping", "delivery", "other", "anything else")))
                .question(
                        "urgency",
                        ScaleQuestion.builder()
                                .text("How urgent is the message?")
                                .level("low")
                                .level("medium", "should be answered today")
                                .level("high")
                                .build())
                .build();

        DecisionResponse response = model().decide(request);

        RecordedRequest sent = requests.get(0);
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.path()).isEqualTo("/v1beta/decisions");
        assertThat(sent.headers()).containsEntry("x-goog-api-key", "test-key");
        assertThat(json(sent.body())).isEqualTo(json("""
                        {
                          "model": "gemini-decision-1",
                          "input": "My card was charged twice, give me my money back!",
                          "questions": {
                            "refund": {"type": "noul", "instructions": "Does the customer ask for a refund?",
                                       "criteria": {"true_description": "they ask for money back",
                                                    "false_description": "anything else"}},
                            "topic": {"type": "choice", "instructions": "What is the message about?",
                                      "criteria": {"billing": "payments", "shipping": "delivery",
                                                   "other": "anything else"}},
                            "urgency": {"type": "score", "instructions": "How urgent is the message?",
                                        "criteria": ["low", "medium: should be answered today", "high"]}
                          }
                        }
                        """));

        assertThat(response.yesNo("refund").probability()).isEqualTo(0.97);
        ChoiceAnswer topic = response.choice("topic");
        assertThat(topic.value()).isEqualTo("billing");
        assertThat(topic.confidence()).isEqualTo(0.9);
        assertThat(topic.probabilities()).containsEntry("billing", 0.9).containsEntry("shipping", 0.06);
        ScaleAnswer urgency = response.scale("urgency");
        assertThat(urgency.mean()).isEqualTo(1.5);
        assertThat(urgency.confidence()).isEqualTo(0.55);
        assertThat(urgency.probabilities()).containsExactly(0.05, 0.4, 0.55);

        assertThat(response.modelName()).isEqualTo("gemini-decision-1");
        assertThat(response.tokenUsage()).isInstanceOf(GoogleGenAiTokenUsage.class);
        GoogleGenAiTokenUsage tokenUsage = (GoogleGenAiTokenUsage) response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(248);
        assertThat(tokenUsage.outputTokenCount()).isZero();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(248);
        assertThat(tokenUsage.cachedContentTokenCount()).isEqualTo(100);
        assertThat(tokenUsage.initialPromptTokenCount()).isEqualTo(200);
    }

    @Test
    void should_not_send_criteria_for_yes_no_question_without_conditions() throws IOException {
        reply(200, YES_NO_ANSWER);

        model().decide(spamRequest());

        assertThat(lastRequestBody().at("/questions/spam"))
                .isEqualTo(json("{\"type\":\"noul\",\"instructions\":\"Is this message spam?\"}"));
    }

    @Test
    void should_send_only_the_condition_that_is_set() throws IOException {
        reply(200, YES_NO_ANSWER);
        DecisionRequest request = DecisionRequest.builder()
                .input("Buy cheap watches now!")
                .question(
                        "spam",
                        YesNoQuestion.builder()
                                .text("Is this message spam?")
                                .yesWhen("it advertises something")
                                .build())
                .build();

        model().decide(request);

        assertThat(lastRequestBody().at("/questions/spam/criteria"))
                .isEqualTo(json("{\"true_description\":\"it advertises something\"}"));
    }

    @Test
    void should_send_contents_with_their_mime_types() throws IOException {
        reply(200, YES_NO_ANSWER);
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(
                        TextContent.from("Attachments of the ticket:"),
                        ImageContent.from("aW1hZ2U=", "image/png"),
                        PdfFileContent.from("cGRm", "application/pdf"),
                        AudioContent.from("YXVkaW8=", "audio/mp3"),
                        VideoContent.from("dmlkZW8=", "video/mp4")))
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build();

        model().decide(request);

        assertThat(lastRequestBody().get("input")).isEqualTo(json("""
                        [
                          {"type": "text", "text": "Attachments of the ticket:"},
                          {"type": "image", "data": "aW1hZ2U=", "mime_type": "image/png", "resolution": "low"},
                          {"type": "document", "data": "cGRm", "mime_type": "application/pdf"},
                          {"type": "audio", "data": "YXVkaW8=", "mime_type": "audio/mp3"},
                          {"type": "video", "data": "dmlkZW8=", "mime_type": "video/mp4"}
                        ]
                        """));
    }

    @Test
    void should_send_files_referenced_by_uri() throws IOException {
        reply(200, YES_NO_ANSWER);
        String fileUri = "https://generativelanguage.googleapis.com/v1beta/files/abc123";
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(
                        ImageContent.from(URI.create(fileUri)),
                        PdfFileContent.from(URI.create(fileUri)),
                        AudioContent.from(URI.create(fileUri))))
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build();

        model().decide(request);

        assertThat(lastRequestBody().get("input")).isEqualTo(json("""
                        [
                          {"type": "image", "uri": "%s", "resolution": "low"},
                          {"type": "document", "uri": "%s", "mime_type": "application/pdf"},
                          {"type": "audio", "uri": "%s"}
                        ]
                        """.formatted(fileUri, fileUri, fileUri)));
    }

    @Test
    void should_send_binary_audio_as_base64() throws IOException {
        reply(200, YES_NO_ANSWER);
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(AudioContent.from(dev.langchain4j.data.audio.Audio.builder()
                        .binaryData("audio".getBytes(StandardCharsets.UTF_8))
                        .mimeType("audio/wav")
                        .build())))
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build();

        model().decide(request);

        assertThat(lastRequestBody().get("input"))
                .isEqualTo(json("[{\"type\": \"audio\", \"data\": \"YXVkaW8=\", \"mime_type\": \"audio/wav\"}]"));
    }

    @ParameterizedTest
    @CsvSource({"LOW, low", "MEDIUM, medium", "HIGH, high", "ULTRA_HIGH, ultra_high"})
    void should_send_image_detail_level_as_resolution(ImageContent.DetailLevel detailLevel, String resolution)
            throws IOException {
        reply(200, YES_NO_ANSWER);
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(ImageContent.from("aW1hZ2U=", "image/png", detailLevel)))
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build();

        model().decide(request);

        assertThat(lastRequestBody().at("/input/0/resolution").asText()).isEqualTo(resolution);
    }

    @Test
    void should_leave_resolution_to_the_model_for_auto_detail_level() throws IOException {
        reply(200, YES_NO_ANSWER);
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(ImageContent.from("aW1hZ2U=", "image/png", ImageContent.DetailLevel.AUTO)))
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build();

        model().decide(request);

        assertThat(lastRequestBody().at("/input/0").has("resolution")).isFalse();
    }

    @Test
    void should_send_fields_as_labeled_contents() throws IOException {
        reply(200, YES_NO_ANSWER);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("subject", "Cheap watches");
        fields.put("price", 42);
        fields.put("photo", ImageContent.from("aW1hZ2U=", "image/png"));
        DecisionRequest request = DecisionRequest.builder()
                .inputFields(fields)
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build();

        model().decide(request);

        assertThat(lastRequestBody().get("input")).isEqualTo(json("""
                        [
                          {"type": "text", "text": "subject: \\"Cheap watches\\""},
                          {"type": "text", "text": "price: 42"},
                          {"type": "text", "text": "photo:"},
                          {"type": "image", "data": "aW1hZ2U=", "mime_type": "image/png", "resolution": "low"}
                        ]
                        """));
    }

    @Test
    void should_send_conversation_as_transcript_followed_by_its_images() throws IOException {
        reply(200, YES_NO_ANSWER);
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getOrder")
                .arguments("{\"orderId\": 42}")
                .build();
        DecisionRequest request = DecisionRequest.builder()
                .inputMessages(List.of(
                        UserMessage.from(
                                TextContent.from("Is my order damaged?"), ImageContent.from("aW1hZ2U=", "image/png")),
                        AiMessage.from(List.of(toolCall)),
                        ToolExecutionResultMessage.from(toolCall, "{\"status\": \"delivered\"}")))
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build();

        model().decide(request);

        JsonNode input = lastRequestBody().get("input");
        assertThat(input).hasSize(3);
        assertThat(input.get(0).get("text").asText())
                .startsWith("messages: ")
                .contains("\"role\":\"user\"", "\"role\":\"assistant\"", "\"role\":\"tool\"", "getOrder");
        assertThat(input.get(1)).isEqualTo(json("{\"type\": \"text\", \"text\": \"[image 1]:\"}"));
        assertThat(input.get(2))
                .isEqualTo(
                        json(
                                "{\"type\": \"image\", \"data\": \"aW1hZ2U=\", \"mime_type\": \"image/png\", \"resolution\": \"low\"}"));
    }

    @Test
    void should_reject_unsupported_content_without_calling_the_api() {
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(new CustomContent()))
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build();

        assertThatThrownBy(() -> model().decide(request))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("GoogleGenAiDecisionModel supports text, image, PDF, audio and video input, but the input "
                        + "contains " + CustomContent.class.getName());
        assertThat(requests).isEmpty();
    }

    @Test
    void should_reject_base64_image_without_mime_type_without_calling_the_api() {
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(ImageContent.from(dev.langchain4j.data.image.Image.builder()
                        .base64Data("aW1hZ2U=")
                        .build())))
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build();

        assertThatThrownBy(() -> model().decide(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The MIME type of an image in base64 must be set");
        assertThat(requests).isEmpty();
    }

    @Test
    void should_answer_every_question_with_a_refusal_when_the_input_is_blocked() {
        reply(200, "{\"answers\":{},\"prompt_feedback\":{\"block_reason\":\"safety\"}}");
        DecisionRequest request = DecisionRequest.builder()
                .input("...")
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .question("topic", ChoiceQuestion.from("What is it about?", List.of("billing", "other")))
                .build();

        DecisionResponse response = model().decide(request);

        assertThat(response.isRefused("spam")).isTrue();
        assertThat(response.isRefused("topic")).isTrue();
        assertThatThrownBy(() -> response.yesNo("spam")).isInstanceOf(ContentFilteredException.class);
    }

    @Test
    void should_use_model_name_from_request() throws IOException {
        reply(200, YES_NO_ANSWER);
        DecisionRequest request = DecisionRequest.builder()
                .input("Buy cheap watches now!")
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .parameters(DecisionRequestParameters.builder()
                        .modelName("gemini-decision-2")
                        .build())
                .build();

        DecisionResponse response = model().decide(request);

        assertThat(lastRequestBody().get("model").asText()).isEqualTo("gemini-decision-2");
        assertThat(response.modelName()).isEqualTo("gemini-decision-2");
    }

    @Test
    void should_require_model_name() {
        GoogleGenAiDecisionModel model = modelBuilder().modelName(null).build();

        assertThatThrownBy(() -> model.decide(spamRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The model name must be set, either on the decision model builder with modelName(...) "
                        + "or on the request with DecisionRequestParameters.builder().modelName(...)");
        assertThat(requests).isEmpty();
    }

    @Test
    void should_send_custom_headers() {
        reply(200, YES_NO_ANSWER);

        modelBuilder().customHeaders(Map.of("x-custom", "value")).build().decide(spamRequest());

        assertThat(requests.get(0).headers()).containsEntry("x-custom", "value");
    }

    @ParameterizedTest
    @CsvSource({
        "400, dev.langchain4j.exception.InvalidRequestException",
        "401, dev.langchain4j.exception.AuthenticationException",
        "403, dev.langchain4j.exception.AuthenticationException",
        "404, dev.langchain4j.exception.ModelNotFoundException",
        "429, dev.langchain4j.exception.RateLimitException",
        "500, dev.langchain4j.exception.InternalServerException"
    })
    void should_map_http_errors_to_langchain4j_exceptions(int status, Class<?> exceptionType) {
        reply(status, "{\"error\":{\"message\":\"failed\",\"code\":\"error\"}}");

        assertThatThrownBy(() -> model().decide(spamRequest())).isInstanceOf(exceptionType);
    }

    @Test
    void should_retry_server_errors() {
        reply(500, "{\"error\":{\"message\":\"failed\",\"code\":\"internal\"}}");
        reply(200, YES_NO_ANSWER);

        DecisionResponse response = modelBuilder().maxRetries(1).build().decide(spamRequest());

        assertThat(response.yesNo("spam").probability()).isEqualTo(0.92);
        assertThat(requests).hasSize(2);
    }

    @Test
    void should_not_retry_server_errors_when_max_retries_is_zero() {
        reply(500, "{\"error\":{\"message\":\"failed\",\"code\":\"internal\"}}");

        assertThatThrownBy(() -> modelBuilder().maxRetries(0).build().decide(spamRequest()))
                .isInstanceOf(InternalServerException.class);
        assertThat(requests).hasSize(1);
    }

    @Test
    void should_not_retry_invalid_requests() {
        reply(400, "{\"error\":{\"message\":\"failed\",\"code\":\"invalid_request\"}}");

        assertThatThrownBy(() -> modelBuilder().maxRetries(2).build().decide(spamRequest()))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(requests).hasSize(1);
    }

    @Test
    void should_decide_asynchronously() {
        reply(200, YES_NO_ANSWER);

        DecisionResponse response = model().decideAsync(spamRequest()).join();

        assertThat(response.yesNo("spam").probability()).isEqualTo(0.92);
        assertThat(requests.get(0).path()).isEqualTo("/v1beta/decisions");
    }

    @Test
    void should_map_http_errors_asynchronously() {
        reply(404, "{\"error\":{\"message\":\"Requested model not found.\",\"code\":\"not_found\"}}");

        assertThatThrownBy(() -> model().decideAsync(spamRequest()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void should_fail_with_invalid_decision_response_exception_when_a_probability_is_out_of_range() {
        reply(200, "{\"answers\":{\"spam\":{\"type\":\"noul\",\"noul\":1.5,\"confidence\":1.5}}}");

        assertThatThrownBy(() -> model().decide(spamRequest()))
                .isInstanceOf(InvalidDecisionResponseException.class)
                .hasMessage("The answer to question 'spam' has an invalid noul: 1.5");
    }

    @Test
    void should_fail_with_invalid_decision_response_exception_when_a_level_is_unknown() {
        reply(
                200,
                "{\"answers\":{\"urgency\":{\"type\":\"score\",\"score\":0.5,\"confidence\":0.5,"
                        + "\"probabilities\":{\"0\":0.5,\"5\":0.5}}}}");
        DecisionRequest request = DecisionRequest.builder()
                .input("...")
                .question("urgency", ScaleQuestion.from("How urgent?", List.of("low", "high")))
                .build();

        assertThatThrownBy(() -> model().decide(request))
                .isInstanceOf(InvalidDecisionResponseException.class)
                .hasMessage(
                        "The answer to question 'urgency' has a probability for the level 5, but the levels are 0 to 1");
    }

    @Test
    void should_fail_with_invalid_decision_response_exception_when_an_answer_is_for_an_unknown_question() {
        reply(200, "{\"answers\":{\"other\":{\"type\":\"noul\",\"noul\":0.5,\"confidence\":0.5}}}");

        assertThatThrownBy(() -> model().decide(spamRequest()))
                .isInstanceOf(InvalidDecisionResponseException.class)
                .hasMessage("The response contains an answer to the unknown question 'other'");
    }

    @Test
    void should_accept_missing_usage() {
        reply(200, "{\"answers\":{\"spam\":{\"type\":\"noul\",\"noul\":0.92,\"confidence\":0.92}}}");

        DecisionResponse response = model().decide(spamRequest());

        assertThat(response.tokenUsage()).isNull();
    }

    @Test
    void should_report_google_genai_provider() {
        assertThat(model().provider()).isEqualTo(ModelProvider.GOOGLE_GENAI);
    }

    static class CustomContent implements Content {

        @Override
        public ContentType type() {
            return ContentType.TEXT;
        }
    }
}
