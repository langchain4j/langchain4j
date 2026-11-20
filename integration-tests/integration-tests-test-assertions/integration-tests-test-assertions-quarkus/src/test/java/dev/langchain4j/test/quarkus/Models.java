package dev.langchain4j.test.quarkus;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import io.quarkus.test.junit.QuarkusMock;
import java.util.List;

/**
 * Installs the model mocks of langchain4j-test as the model beans of the application for the current test.
 */
final class Models {

    private Models() {}

    static MockChatModel useChatModel(MockChatModel chatModel) {
        QuarkusMock.installMockForType(chatModel, ChatModel.class);
        return chatModel;
    }

    static MockChatModel useChatModelResponding(AiMessage... responses) {
        return useChatModel(MockChatModel.thatResponds(List.of(responses)));
    }

    static MockStreamingChatModel useStreamingChatModel(MockStreamingChatModel streamingChatModel) {
        QuarkusMock.installMockForType(streamingChatModel, StreamingChatModel.class);
        return streamingChatModel;
    }

    /**
     * An answer that calls the given tool. The mock gives the call an id when it answers.
     */
    static AiMessage toolCall(String toolName, String arguments) {
        return AiMessage.from(MockChatModel.toolCall(toolName, arguments));
    }
}
