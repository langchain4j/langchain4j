package com.example.support;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.function.Supplier;

/**
 * The models of the AI Services, using the shared mocks of langchain4j-core. The AI Services get them with
 * {@code @RegisterAiService(chatLanguageModelSupplier = ...)} or
 * {@code @RegisterAiService(streamingChatLanguageModelSupplier = ...)}.
 */
public final class TestModels {

    static final String STREAMED_ANSWER = "Hello from Miles of Smiles";
    static final TokenUsage TOKEN_USAGE_PER_CALL = new TokenUsage(20, 10);

    private TestModels() {}

    public static class ScriptedChatModel implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return ChatModelMock.thatResponds(SupportScript::respond)
                    .withTokenUsage(TOKEN_USAGE_PER_CALL)
                    .withFinishReason(FinishReason.STOP);
        }
    }

    public static class StreamedAnswerModel implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return StreamingChatModelMock.thatAlwaysStreams("Hello ", "from ", "Miles of Smiles")
                    .withTokenUsage(TOKEN_USAGE_PER_CALL)
                    .withFinishReason(FinishReason.STOP);
        }
    }
}
