package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class MistralAiStreamingChatModelStrictJsonSchemaTest {

    private static final String MODEL = "mistral-small-latest";

    @Test
    void should_send_strict_true_when_strictJsonSchema_is_enabled() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(responseEvents());

        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MODEL)
                .strictJsonSchema(true)
                .build();

        // when
        chat(model);

        // then
        assertThat(mockHttpClient.request().body().replaceAll("\\s", "")).contains("\"strict\":true");
    }

    @Test
    void should_NOT_send_strict_true_when_strictJsonSchema_is_not_set() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(responseEvents());

        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MODEL)
                .build();

        // when
        chat(model);

        // then
        assertThat(mockHttpClient.request().body().replaceAll("\\s", "")).contains("\"strict\":false");
    }

    private static void chat(StreamingChatModel model) {
        ChatRequest request = ChatRequest.builder()
                .messages(dev.langchain4j.data.message.UserMessage.from("What is the answer?"))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Answer")
                                .rootElement(JsonObjectSchema.builder()
                                        .addStringProperty("answer")
                                        .build())
                                .build())
                        .build())
                .build();
        var handler = new TestStreamingChatResponseHandler();
        model.chat(request, handler);
        handler.get();
    }

    private static List<ServerSentEvent> responseEvents() {
        return List.of(
                new ServerSentEvent(null, """
                        {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{"content":[{"type":"text","text":"{\\"answer\\":\\"42\\"}"}]}}]}""".formatted(MODEL)),
                new ServerSentEvent(null, """
                        {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30,"num_cached_tokens":0}}""".formatted(MODEL)),
                new ServerSentEvent(null, "[DONE]"));
    }
}
