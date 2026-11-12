package dev.langchain4j.test.quarkus;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Provides the models of the application in tests. They are application-scoped, so a test can replace them with
 * {@code QuarkusMock.installMockForType(...)}: the AI Services, created once, then use the replacement for that test.
 */
public class TestModels {

    static final String DEFAULT_ANSWER = "How can I help you?";

    @Produces
    @ApplicationScoped
    ChatModel chatModel() {
        return ChatModelMock.thatAlwaysResponds(DEFAULT_ANSWER);
    }

    @Produces
    @ApplicationScoped
    StreamingChatModel streamingChatModel() {
        return StreamingChatModelMock.thatAlwaysStreams("How ", "can ", "I ", "help ", "you?");
    }
}
