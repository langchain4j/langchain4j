package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiServiceValidationTest {

    private static final ChatModel CHAT_MODEL = ChatModelMock.thatAlwaysResponds("Hello there!");

    record Person(String name) {}

    enum Sentiment {
        POSITIVE,
        NEGATIVE
    }

    interface WithChatResponseInResult {
        Result<ChatResponse> chat(String userMessage);
    }

    interface WithTokenStreamInResult {
        Result<TokenStream> chat(String userMessage);
    }

    interface WithNestedResult {
        Result<Result<String>> chat(String userMessage);
    }

    interface WithTokenUsageInResult {
        Result<TokenUsage> chat(String userMessage);
    }

    interface WithImageInResult {
        Result<Image> chat(String userMessage);
    }

    interface WithChatResponseInList {
        List<ChatResponse> chat(String userMessage);
    }

    interface WithAiMessageInSet {
        Set<AiMessage> chat(String userMessage);
    }

    interface WithDocumentInResultOfList {
        Result<List<Document>> chat(String userMessage);
    }

    interface WithTextSegment {
        TextSegment chat(String userMessage);
    }

    interface WithEmbedding {
        Embedding chat(String userMessage);
    }

    interface WithUserMessage {
        UserMessage chat(String userMessage);
    }

    interface WithToolExecutionRequest {
        ToolExecutionRequest chat(String userMessage);
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                WithChatResponseInResult.class,
                WithTokenStreamInResult.class,
                WithNestedResult.class,
                WithTokenUsageInResult.class,
                WithImageInResult.class,
                WithChatResponseInList.class,
                WithAiMessageInSet.class,
                WithDocumentInResultOfList.class,
                WithTextSegment.class,
                WithEmbedding.class,
                WithUserMessage.class,
                WithToolExecutionRequest.class
            })
    void should_reject_langchain4j_types_as_content_type(Class<?> aiServiceClass) {
        assertThatThrownBy(() ->
                        AiServices.builder(aiServiceClass).chatModel(CHAT_MODEL).build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("is a LangChain4j type")
                .hasMessageContaining("chat");
    }

    interface WithString {
        String chat(String userMessage);
    }

    interface WithPojo {
        Person chat(String userMessage);
    }

    interface WithEnum {
        Sentiment chat(String userMessage);
    }

    interface WithVoid {
        void chat(String userMessage);
    }

    interface WithStringInResult {
        Result<String> chat(String userMessage);
    }

    interface WithPojoListInResult {
        Result<List<Person>> chat(String userMessage);
    }

    interface WithAiMessageInResult {
        Result<AiMessage> chat(String userMessage);
    }

    interface WithVoidInResult {
        Result<Void> chat(String userMessage);
    }

    interface WithResponseInResult {
        Result<Response<AiMessage>> chat(String userMessage);
    }

    interface WithAiMessage {
        AiMessage chat(String userMessage);
    }

    interface WithChatResponse {
        ChatResponse chat(String userMessage);
    }

    interface WithResponse {
        Response<AiMessage> chat(String userMessage);
    }

    interface WithImage {
        Image chat(String userMessage);
    }

    interface WithImageContentList {
        List<ImageContent> chat(String userMessage);
    }

    interface WithDefaultMethodReturningLangChain4jType {

        String chat(String userMessage);

        default Result<ChatResponse> notAnAiServiceMethod() {
            return null;
        }
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                WithString.class,
                WithPojo.class,
                WithEnum.class,
                WithVoid.class,
                WithStringInResult.class,
                WithPojoListInResult.class,
                WithAiMessageInResult.class,
                WithVoidInResult.class,
                WithResponseInResult.class,
                WithAiMessage.class,
                WithChatResponse.class,
                WithResponse.class,
                WithImage.class,
                WithImageContentList.class,
                WithDefaultMethodReturningLangChain4jType.class
            })
    void should_allow_supported_return_types(Class<?> aiServiceClass) {
        assertThatCode(() ->
                        AiServices.builder(aiServiceClass).chatModel(CHAT_MODEL).build())
                .doesNotThrowAnyException();
    }
}
