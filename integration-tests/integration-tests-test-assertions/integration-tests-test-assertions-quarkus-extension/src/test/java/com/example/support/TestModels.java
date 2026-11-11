package com.example.support;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.model.output.TokenUsage;
import java.util.function.Supplier;

/**
 * The models of the AI Services, using the mocks of langchain4j-test. The AI Services get them with
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
            return MockChatModel.thatResponds(SupportScript::respond).thenRepeats().withTokenUsage(TOKEN_USAGE_PER_CALL);
        }
    }

    public static class StreamedAnswerModel implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return MockStreamingChatModel.thatStreams(STREAMED_ANSWER).thenRepeats().withTokenUsage(TOKEN_USAGE_PER_CALL);
        }
    }
}
