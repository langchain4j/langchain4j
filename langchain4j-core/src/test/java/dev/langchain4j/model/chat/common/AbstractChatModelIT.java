package dev.langchain4j.model.chat.common;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * Contains all the common tests that every {@link ChatModel} must successfully pass.
 * This ensures that {@link ChatModel} implementations are interchangeable among themselves.
 */
@TestInstance(PER_CLASS)
public abstract class AbstractChatModelIT extends AbstractBaseChatModelIT<ChatModel> {

    @Override
    protected ChatResponseAndStreamingMetadata chat(ChatModel chatModel, ChatRequest chatRequest) {
        ChatResponse chatResponse = chatModel.chat(chatRequest);
        return new ChatResponseAndStreamingMetadata(chatResponse, null);
    }

    /** Override to {@code true} once the model implements the non-blocking {@link ChatModel#chatAsync(ChatRequest)}. */
    protected boolean supportsChatAsync() {
        return false;
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsChatAsync")
    protected void should_chat_asynchronously(ChatModel model) throws Exception {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        ChatResponse chatResponse = model.chatAsync(chatRequest).get(60, SECONDS);

        // then
        assertThat(chatResponse.aiMessage().text()).containsIgnoringCase("Berlin");
    }
}
