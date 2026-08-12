package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InternalOllamaHelperTest {

    @Test
    void toOllamaMessages_concatenatesMultipleTextContents() {
        UserMessage userMessage =
                UserMessage.from(TextContent.from("Hello"), TextContent.from("world"), TextContent.from("!"));

        List<Message> messages = InternalOllamaHelper.toOllamaMessages(List.<ChatMessage>of(userMessage));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("Hello\nworld\n!");
        assertThat(messages.get(0).getImages()).isNull();
    }

    @Test
    void toOllamaMessages_concatenatesMultipleTextContentsWithImage() {
        UserMessage userMessage = UserMessage.from(
                TextContent.from("Describe these"),
                TextContent.from("two parts"),
                ImageContent.from("aW1hZ2U=", "image/png"));

        List<Message> messages = InternalOllamaHelper.toOllamaMessages(List.<ChatMessage>of(userMessage));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("Describe these\ntwo parts");
        assertThat(messages.get(0).getImages()).containsExactly("aW1hZ2U=");
    }

    @Test
    void toOllamaMessages_imageOnlyMessageHasEmptyContent() {
        UserMessage userMessage = UserMessage.from(ImageContent.from("aW1hZ2U=", "image/png"));

        List<Message> messages = InternalOllamaHelper.toOllamaMessages(List.<ChatMessage>of(userMessage));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEmpty();
        assertThat(messages.get(0).getImages()).containsExactly("aW1hZ2U=");
    }

    @Test
    void toToolExecutionRequests_mapsToolCalls() {
        ToolCall toolCall = ToolCall.builder()
                .id("tool-1")
                .function(FunctionCall.builder()
                        .name("lookupWeather")
                        .arguments(Map.of("city", "Shanghai"))
                        .build())
                .build();

        List<ToolExecutionRequest> result = InternalOllamaHelper.toToolExecutionRequests(List.of(toolCall));

        assertThat(result)
                .containsExactly(ToolExecutionRequest.builder()
                        .id("tool-1")
                        .name("lookupWeather")
                        .arguments("{\"city\":\"Shanghai\"}")
                        .build());
    }

    @Test
    void toToolExecutionRequests_handlesEmptyToolCalls() {
        List<ToolExecutionRequest> result = InternalOllamaHelper.toToolExecutionRequests(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void toOllamaChatRequest_carriesTruncate() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(OllamaChatRequestParameters.builder()
                        .modelName("llama3")
                        .truncate(false)
                        .build())
                .build();

        OllamaChatRequest result = InternalOllamaHelper.toOllamaChatRequest(chatRequest, false);

        assertThat(result.getTruncate()).isFalse();
    }

    @Test
    void toOllamaChatRequest_leavesTruncateNullWhenNotSet() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(OllamaChatRequestParameters.builder()
                        .modelName("llama3")
                        .build())
                .build();

        OllamaChatRequest result = InternalOllamaHelper.toOllamaChatRequest(chatRequest, false);

        assertThat(result.getTruncate())
                .as("the field must be absent from the request so the server default applies")
                .isNull();
    }

    @Test
    void toOllamaChatRequest_serializesTruncateAsATopLevelField() throws Exception {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(OllamaChatRequestParameters.builder()
                        .modelName("llama3")
                        .numCtx(256)
                        .truncate(false)
                        .build())
                .build();

        String json =
                new ObjectMapper().writeValueAsString(InternalOllamaHelper.toOllamaChatRequest(chatRequest, false));

        // truncate is a sibling of model and messages, not a member of options, which carries num_ctx.
        assertThat(json).contains("\"truncate\":false");
        assertThat(json).contains("\"num_ctx\":256");
        assertThat(json.replaceAll("\"options\":\\{[^}]*}", "")).contains("\"truncate\":false");
    }

    @Test
    void toOllamaChatRequest_omitsTruncateWhenNotSet() throws Exception {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(OllamaChatRequestParameters.builder()
                        .modelName("llama3")
                        .build())
                .build();

        String json =
                new ObjectMapper().writeValueAsString(InternalOllamaHelper.toOllamaChatRequest(chatRequest, false));

        assertThat(json).doesNotContain("truncate");
    }
}
