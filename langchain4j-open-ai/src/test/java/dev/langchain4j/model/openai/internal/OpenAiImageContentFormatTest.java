package dev.langchain4j.model.openai.internal;

import static dev.langchain4j.model.openai.internal.OpenAiUtils.toOpenAiMessages;
import static dev.langchain4j.model.openai.internal.chat.ContentType.IMAGE_URL;
import static dev.langchain4j.model.openai.internal.chat.ContentType.INPUT_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.internal.chat.Content;
import dev.langchain4j.model.openai.internal.chat.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiImageContentFormatTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_use_chat_completions_image_url_format_by_default() throws Exception {
        // given
        ImageContent imageContent = new ImageContent("base64data", "image/jpeg", ImageContent.DetailLevel.LOW);
        dev.langchain4j.data.message.UserMessage userMessage =
                dev.langchain4j.data.message.UserMessage.from(imageContent);

        // when
        UserMessage openAiMessage =
                (UserMessage) toOpenAiMessages(List.of(userMessage)).get(0);

        // then
        @SuppressWarnings("unchecked")
        List<Content> contents = (List<Content>) openAiMessage.content();
        Content content = contents.get(0);
        assertThat(content.type()).isEqualTo(IMAGE_URL);
        assertThat(content.imageUrl().getUrl()).isEqualTo("data:image/jpeg;base64,base64data");
        assertThat(content.imageUrl().getDetail().name()).isEqualTo("LOW");
        assertThat(content.inputImageUrl()).isNull();

        String json = OBJECT_MAPPER.writeValueAsString(content);
        assertThat(json)
                .contains("\"type\":\"image_url\"")
                .contains("\"image_url\":{\"url\":\"data:image/jpeg;base64,base64data\",\"detail\":\"low\"}");
    }

    @Test
    void should_use_input_image_format_when_configured() throws Exception {
        // given
        ImageContent imageContent = new ImageContent("base64data", "image/jpeg", ImageContent.DetailLevel.LOW);
        dev.langchain4j.data.message.UserMessage userMessage =
                dev.langchain4j.data.message.UserMessage.from(imageContent);

        // when
        UserMessage openAiMessage = (UserMessage)
                toOpenAiMessages(List.of(userMessage), false, null, true).get(0);

        // then
        @SuppressWarnings("unchecked")
        List<Content> contents = (List<Content>) openAiMessage.content();
        Content content = contents.get(0);
        assertThat(content.type()).isEqualTo(INPUT_IMAGE);
        assertThat(content.inputImageUrl()).isEqualTo("data:image/jpeg;base64,base64data");
        assertThat(content.imageUrl()).isNull();

        String json = OBJECT_MAPPER.writeValueAsString(content);
        assertThat(json)
                .contains("\"type\":\"input_image\"")
                .contains("\"image_url\":\"data:image/jpeg;base64,base64data\"")
                .doesNotContain("detail");
    }

    @Test
    void chat_model_should_use_image_url_format_by_default() throws Exception {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(successfulChatCompletionResponse());
        OpenAiChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .build();

        // when
        model.chat(chatRequestWithImage());

        // then
        JsonNode content = firstRequestContent(mockHttpClient);
        assertThat(content.get("type").asText()).isEqualTo("image_url");
        assertThat(content.get("image_url").isObject()).isTrue();
        assertThat(content.get("image_url").get("url").asText()).isEqualTo("data:image/jpeg;base64,base64data");
        assertThat(content.get("image_url").get("detail").asText()).isEqualTo("low");
    }

    @Test
    void chat_model_should_use_input_image_format_when_configured() throws Exception {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(successfulChatCompletionResponse());
        OpenAiChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .useInputImageFormat(true)
                .build();

        // when
        model.chat(chatRequestWithImage());

        // then
        JsonNode content = firstRequestContent(mockHttpClient);
        assertThat(content.get("type").asText()).isEqualTo("input_image");
        assertThat(content.get("image_url").isTextual()).isTrue();
        assertThat(content.get("image_url").asText()).isEqualTo("data:image/jpeg;base64,base64data");
        assertThat(content.has("detail")).isFalse();
    }

    @Test
    void streaming_chat_model_should_use_input_image_format_when_configured() throws Exception {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(successfulStreamingChatCompletionEvents());
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .useInputImageFormat(true)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequestWithImage(), handler);
        handler.get();

        // then
        JsonNode content = firstRequestContent(mockHttpClient);
        assertThat(content.get("type").asText()).isEqualTo("input_image");
        assertThat(content.get("image_url").isTextual()).isTrue();
        assertThat(content.get("image_url").asText()).isEqualTo("data:image/jpeg;base64,base64data");
        assertThat(content.has("detail")).isFalse();
    }

    @Test
    void should_deserialize_chat_completions_image_url_format() throws Exception {
        // given
        String json =
                "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/jpeg;base64,base64data\",\"detail\":\"low\"}}";

        // when
        Content content = OBJECT_MAPPER.readValue(json, Content.class);

        // then
        assertThat(content.type()).isEqualTo(IMAGE_URL);
        assertThat(content.imageUrl()).isNotNull();
        assertThat(content.imageUrl().getUrl()).isEqualTo("data:image/jpeg;base64,base64data");
        assertThat(content.imageUrl().getDetail().name()).isEqualTo("LOW");
        assertThat(content.inputImageUrl()).isNull();
    }

    @Test
    void should_deserialize_input_image_format() throws Exception {
        // given
        String json = "{\"type\":\"input_image\",\"image_url\":\"data:image/jpeg;base64,base64data\"}";

        // when
        Content content = OBJECT_MAPPER.readValue(json, Content.class);

        // then
        assertThat(content.type()).isEqualTo(INPUT_IMAGE);
        assertThat(content.inputImageUrl()).isEqualTo("data:image/jpeg;base64,base64data");
        assertThat(content.imageUrl()).isNull();
    }

    private static ChatRequest chatRequestWithImage() {
        ImageContent imageContent = new ImageContent("base64data", "image/jpeg", ImageContent.DetailLevel.LOW);
        dev.langchain4j.data.message.UserMessage userMessage =
                dev.langchain4j.data.message.UserMessage.from(imageContent);
        return ChatRequest.builder().messages(userMessage).build();
    }

    private static JsonNode firstRequestContent(MockHttpClient mockHttpClient) throws Exception {
        return OBJECT_MAPPER
                .readTree(mockHttpClient.request().body())
                .get("messages")
                .get(0)
                .get("content")
                .get(0);
    }

    private static SuccessfulHttpResponse successfulChatCompletionResponse() {
        return SuccessfulHttpResponse.builder().statusCode(200).body("""
                        {
                          "id": "chatcmpl-test",
                          "object": "chat.completion",
                          "created": 1,
                          "model": "gpt-4o-mini",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "ok"
                              },
                              "finish_reason": "stop"
                            }
                          ]
                        }
                        """).build();
    }

    private static List<ServerSentEvent> successfulStreamingChatCompletionEvents() {
        return List.of(
                new ServerSentEvent(null, """
                                {
                                  "id": "chatcmpl-test",
                                  "object": "chat.completion.chunk",
                                  "created": 1,
                                  "model": "gpt-4o-mini",
                                  "choices": [
                                    {
                                      "index": 0,
                                      "delta": {
                                        "role": "assistant",
                                        "content": "ok"
                                      },
                                      "finish_reason": null
                                    }
                                  ]
                                }
                                """), new ServerSentEvent(null, """
                                {
                                  "id": "chatcmpl-test",
                                  "object": "chat.completion.chunk",
                                  "created": 1,
                                  "model": "gpt-4o-mini",
                                  "choices": [
                                    {
                                      "index": 0,
                                      "delta": {},
                                      "finish_reason": "stop"
                                    }
                                  ]
                                }
                                """), new ServerSentEvent(null, "[DONE]"));
    }
}
